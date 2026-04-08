package com.riskguard.service;

import com.riskguard.entity.ClauseEntity;
import com.riskguard.entity.DocumentEntity;
import com.riskguard.repo.ClauseRepository;
import com.riskguard.repo.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentService {

    private final TextExtractionService textExtraction;
    private final ClauseSegmentationService segmentation;
    private final DocumentRepository docRepo;
    private final ClauseRepository clauseRepo;

    public DocumentService(
            TextExtractionService t,
            ClauseSegmentationService s,
            DocumentRepository d,
            ClauseRepository c
    ) {
        this.textExtraction = t;
        this.segmentation = s;
        this.docRepo = d;
        this.clauseRepo = c;
    }

    /**
     * Yeni bir dokümanı kaydeder:
     *  - PDF'ten metni çıkarır
     *  - DocumentEntity yaratır
     *  - versionGroupId alanını ayarlar
     *  - clause'lara böler ve kaydeder
     *
     * existingVersionGroupId:
     *   - null ise => bu yepyeni bir sözleşme, kendi grubunu başlatır
     *   - dolu ise => bu, aynı sözleşmenin yeni versiyonu olarak o gruba dahil edilir
     */
    @Transactional
    public DocumentEntity saveDocument(String filename,
                                       InputStream in,
                                       UUID existingVersionGroupId
    ) throws Exception {

        // 1. Metni çıkar
        //    Artık sadece düz metin değil, OCR kullanıldı mı onu da bilmek istiyoruz.
        //    extractWithProvenance bize hem text hem usedOcr döndürüyor.
        // 🔽 EKLENDİ
        ExtractedTextResult extraction = textExtraction.extractWithProvenance(in);
        String text = extraction.text;
        boolean usedOcr = extraction.usedOcr;
        // 🔼 EKLENDİ

        // 2. Dokümanı oluştur
        DocumentEntity doc = new DocumentEntity();

        // Normalde id ve uploadedAt @PrePersist ile atanıyor ama
        // versionGroupId lojik olarak id'ye bağlı olacağı için burada id'yi elle set etmek iyi olur.
        UUID newId = UUID.randomUUID();
        doc.setId(newId);

        doc.setFilename(filename);
        doc.setLanguage(detectLanguage(text));
        doc.setText(text);
        doc.setUploadedAt(Instant.now());

        // 🔽 EKLENDİ: OCR bilgisini kaydet
        // DocumentEntity'ne şu alanı eklemiş olacağız:
        //   private Boolean ocrUsed;
        //   public void setOcrUsed(Boolean ocrUsed) { this.ocrUsed = ocrUsed; }
        //   public Boolean getOcrUsed() { return ocrUsed; }
        doc.setOcrUsed(Boolean.valueOf(usedOcr));
        // 🔼 EKLENDİ

        // Versiyon grubu ataması:
        if (existingVersionGroupId != null) {
            // Bu, mevcut bir sözleşmenin yeni versiyonu
            doc.setVersionGroupId(existingVersionGroupId);
        } else {
            // Bu yepyeni bir sözleşme, kendi grubunu başlatıyor
            doc.setVersionGroupId(newId);
        }

        // DB'ye kaydet
        doc = docRepo.save(doc);

        // 3. Clause'lara böl ve kaydet
        //    Daha zengin segmentation kullanıyoruz: start/end offset bilgisi dahil.
        //    Eskiden: segmentation.splitIntoClauses(text) bize sadece List<String> veriyordu.
        //    Artık splitIntoClausesWithOffsets(text) ile ClauseSegmentationService.ClauseSegment alıyoruz.
        // 🔽 EKLENDİ
        List<ClauseSegmentationService.ClauseSegment> chunksWithOffsets =
                segmentation.splitIntoClausesWithOffsets(text);

        int i = 0;
        for (ClauseSegmentationService.ClauseSegment seg : chunksWithOffsets) {
            ClauseEntity ce = new ClauseEntity();
            ce.setDocumentId(doc.getId());
            ce.setIdx(i++);
            ce.setText(seg.text);

            // ClauseEntity alanlarında varsa dolduruyoruz.
            // Bunlar ClauseEntity'de zaten yoksa hiçbir şey silme — setter yoksa bu iki satır sende derlenmez,
            // o durumda sadece ce.setText(...) ve ce.setIdx(...) kalsın.
            try {
                ce.setSpanStart(seg.start);
                ce.setSpanEnd(seg.end);
            } catch (NoSuchMethodError | NoSuchFieldError ignored) {
                // projenin mevcut modelinde spanStart/spanEnd yoksa sessizce geçeriz
            } catch (Throwable ignored) {
                // yine sessiz geçiyoruz; PoC'te kırmayalım
            }

            // heading varsa (ClauseEntity içinde heading alanı tanımlıysa) doldurabiliriz:
            // örn: "MADDE 5 GİZLİLİK"
            try {
                // basit heuristic: satırın ilk satırını heading'e koy
                String headingGuess = null;
                if (seg.text != null) {
                    // ilk satırı al
                    int nl = seg.text.indexOf('\n');
                    headingGuess = (nl >= 0) ? seg.text.substring(0, nl).trim()
                                             : seg.text.trim();
                }
                ce.setHeading(headingGuess);
            } catch (NoSuchMethodError | NoSuchFieldError ignored) {
                // ClauseEntity.heading yoksa sorun değil
            } catch (Throwable ignored) {
            }

            clauseRepo.save(ce);
        }
        // 🔼 EKLENDİ

        return doc;
    }

    /**
     * Mevcut public endpoint'lerin geriye dönük bozulmaması için
     * eski imzayı da koruyoruz.
     *
     * Bu sürüm hiçbir versiyon grubuna bağlamıyor,
     * her belgeyi kendi başına yeni bir grup olarak kaydediyor.
     *
     * Controller şu an bunu kullanıyorsa dokunmadan çalışmaya devam eder.
     */
    @Transactional
    public DocumentEntity saveDocument(String filename, InputStream in) throws Exception {
        // existingVersionGroupId=null => yeni grup oluştur
        return saveDocument(filename, in, null);
    }

    /**
     * Basit dil tespiti:
     * - Türkçe karakter varsa 'tr'
     * - yoksa 'en'
     */
    private String detectLanguage(String text) {
        if (text != null && text.matches(".*[çğıöşüÇĞİÖŞÜ].*")) {
            return "tr";
        }
        return "en";
    }

    /**
     * Bir dokümana ait clause'ları sırayla döndür.
     */
    public List<ClauseEntity> getClauses(UUID documentId) {
        return clauseRepo.findByDocumentIdOrderByIdxAsc(documentId);
    }

    /**
     * Dokümanı ID'ye göre getir.
     */
    public Optional<DocumentEntity> findDocument(UUID id) {
        return docRepo.findById(id);
    }

    /**
     * Aynı version_group_id'ye sahip olan tüm dokümanları
     * yüklenme zamanına göre (eski -> yeni) sıralı döndür.
     *
     * Bu metot AnalysisService.buildReport() içinde
     * deltaScore hesaplamak için kullanılıyor.
     */
    public List<DocumentEntity> findByVersionGroupOrdered(UUID versionGroupId) {
        return docRepo.findByVersionGroupIdOrderByUploadedAtAsc(versionGroupId);
    }
}
