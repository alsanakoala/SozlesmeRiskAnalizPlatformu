package com.riskguard.service;

import com.riskguard.dto.AnalyzeResponse;
import com.riskguard.dto.RiskFindingDTO;
import com.riskguard.dto.RiskReportResponse;
import com.riskguard.entity.ClauseEntity;
import com.riskguard.entity.DocumentEntity;
import com.riskguard.entity.RiskFindingEntity;
import com.riskguard.repo.ClauseRepository;
import com.riskguard.repo.RiskFindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class AnalysisService {

    private final DocumentService documentService;
    private final RuleEngineService ruleEngine;
    private final RiskFindingRepository findingRepo;
    private final ClauseRepository clauseRepo;

    public AnalysisService(
            DocumentService ds,
            RuleEngineService re,
            RiskFindingRepository fr,
            ClauseRepository cr
    ) {
        this.documentService = ds;
        this.ruleEngine = re;
        this.findingRepo = fr;
        this.clauseRepo = cr;
    }

    /**
     * Yeni bir analiz çalıştırır:
     * - Clause'ları alır
     * - Rule engine ile değerlendirir
     * - DB'ye bulguları yazar
     * - Anında AnalyzeResponse DTO'su döner
     */
    @Transactional
    public AnalyzeResponse analyze(UUID documentId) {

        // Sözleşmedeki tüm clause'ları getir
        List<ClauseEntity> clauses = documentService.getClauses(documentId);

        // Değiştirilebilir listeler oluşturuyoruz (Full text ekleyebilmek için)
        List<UUID> clauseIds = clauses.stream()
                .map(ClauseEntity::getId)
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> texts = clauses.stream()
                .map(ClauseEntity::getText)
                .collect(Collectors.toCollection(ArrayList::new));

        // 🔽 EKLEME: Bütünsel analiz için döküman metnini listeye ekle
        Optional<DocumentEntity> docOpt = documentService.findDocument(documentId);
        if (docOpt.isPresent()) {
            DocumentEntity doc = docOpt.get();
            String fullText = doc.getText();
            if (fullText != null && !fullText.trim().isEmpty()) {
                texts.add(fullText);
                clauseIds.add(null); // Clause ID null olunca veritabanında FK hatası oluşmaz
            }
        }
        // 🔼 EKLEME BİTTİ

        // Kural motoru çağrısı -> RiskFindingEntity listesi döner
        List<RiskFindingEntity> findings = ruleEngine.evaluate(documentId, clauseIds, texts);

        // DB'ye kaydet
        findingRepo.saveAll(findings);

        // Response DTO'sunu hazırla
        AnalyzeResponse resp = new AnalyzeResponse();
        resp.score = ruleEngine.aggregateScore(findings);

        resp.findings = findings.stream().map(f -> {
            RiskFindingDTO d = new RiskFindingDTO();
            d.id = f.getId();
            d.clauseId = f.getClauseId();
            d.category = f.getCategory();
            d.ruleId = f.getRuleId();
            d.score = f.getScore();
            d.confidence = f.getConfidence();
            d.snippet = f.getSnippet();
            d.explanation = f.getExplanation();
            d.mitigation = f.getMitigation();
            return d;
        }).collect(Collectors.toList());

        // 🔽 DEBUG BİLGİLERİ
        resp.clauseCount = (clauses != null ? clauses.size() : 0);
        resp.findingCount = (findings != null ? findings.size() : 0);

        if (docOpt.isPresent()) {
            DocumentEntity doc = docOpt.get();
            String fullText = doc.getText();
            resp.textLength = (fullText != null ? fullText.length() : 0);
        } else {
            resp.textLength = 0;
        }
        // 🔼 DEBUG BİLGİLERİ

        return resp;
    }

    /**
     * Daha sonra görüntülenecek / raporlanacak final rapor objesi.
     */
    public RiskReportResponse buildReport(UUID documentId) {
        // 1. Belgeyi al
        Optional<DocumentEntity> docOpt = documentService.findDocument(documentId);
        if (docOpt.isEmpty()) {
            throw new RuntimeException("Document not found: " + documentId);
        }
        DocumentEntity doc = docOpt.get();

        // 2. Mevcut bulguları al
        List<RiskFindingEntity> findingsList = findingRepo.findByDocumentId(documentId);

        // 🔽 EKLENDİ: clause ve metin istatistikleri
        List<ClauseEntity> clausesForDoc = documentService.getClauses(documentId);
        int clauseCountVal = (clausesForDoc != null ? clausesForDoc.size() : 0);

        String fullText = doc.getText();
        int textLenVal = (fullText != null ? fullText.length() : 0);
        // 🔼 EKLENDİ

        // 3. Toplam skoru yeniden hesapla
        double totalScore = ruleEngine.aggregateScore(findingsList);

        // 🔽 EKLENDİ: kategori bazlı ortalama skor hesapla
        Map<String, List<Double>> buckets = new HashMap<>();
        for (RiskFindingEntity f : findingsList) {
            String cat = (f.getCategory() != null ? f.getCategory().name() : "OTHER");

            Double rawScore = f.getScore();
            double sc = (rawScore != null ? rawScore.doubleValue() : 0.0);

            buckets.computeIfAbsent(cat, k -> new ArrayList<>()).add(sc);
        }

        Map<String, Double> avgMap = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : buckets.entrySet()) {
            List<Double> vals = entry.getValue();
            double sum = 0.0;
            for (Double d : vals) {
                if (d != null) {
                    sum += d;
                }
            }
            double avg = vals.isEmpty() ? 0.0 : (sum / vals.size());
            avgMap.put(entry.getKey(), avg);
        }
        // 🔼 EKLENDİ

        // 4. DTO'ya doldur
        RiskReportResponse resp = new RiskReportResponse();
        resp.documentId = documentId.toString();
        resp.filename = doc.getFilename();
        resp.language = doc.getLanguage();
        resp.totalScore = totalScore;

        resp.clauseCount = clauseCountVal;
        resp.textLength = textLenVal;
        resp.findingCount = (findingsList != null ? findingsList.size() : 0);

        resp.ocrUsed = doc.getOcrUsed();
        resp.avgScorePerCategory = avgMap;

        resp.findings = findingsList.stream().map(f -> {
            RiskReportResponse.FindingSummary fs = new RiskReportResponse.FindingSummary();

            fs.category = (f.getCategory() != null ? f.getCategory().name() : null);
            fs.ruleId = f.getRuleId();
            fs.score = f.getScore();
            fs.confidence = f.getConfidence();
            fs.snippet = f.getSnippet();
            fs.explanation = f.getExplanation();
            fs.mitigation = f.getMitigation();

            fs.humanTitle = getHumanTitleForCategory(fs.category);
            fs.advice = getAdviceForCategory(fs.category);

            return fs;
        }).collect(Collectors.toList());

        return resp;
    }

    private String getHumanTitleForCategory(String category) {
        if (category == null) return null;
        switch (category) {
            case "INDEMNITY":
                return "Geniş Tazminat Yükümlülüğü";
            case "LIMITATION_OF_LIABILITY":
                return "Sorumluluk Sınırı / Limit Yok";
            case "TERMINATION":
                return "Tek Taraflı Fesih Hakkı";
            case "CONFIDENTIALITY":
                return "Gizlilik Yükümlülüğü";
            case "GOVERNING_LAW":
                return "Uyuşmazlıkta Uygulanacak Hukuk";
            case "JURISDICTION":
                return "Yetkili Mahkeme / Yargı Yeri";
            case "AUTO_RENEWAL":
                return "Otomatik Yenileme Riski";
            case "PAYMENT_TERMS":
                return "Ödeme Şartları";
            case "SLA":
                return "Hizmet Seviyesi Taahhüdü (SLA)";
            case "DATA_PROTECTION":
                return "Kişisel Veri / KVKK Riski";
            case "INSURANCE_OBLIGATION":
                return "Sigorta Yükümlülüğü";
            case "INTELLECTUAL_PROPERTY_TRANSFER":
                return "Fikri Mülkiyet Devri";
            default:
                return "Diğer Riskli Madde";
        }
    }

    private String getAdviceForCategory(String category) {
        if (category == null) return null;
        switch (category) {
            case "INDEMNITY":
                return "Karşı taraf senden çok geniş (neredeyse sınırsız) tazminat talep ediyor. Bu madde daraltılmalı veya karşılıklı hale getirilmeli.";
            case "LIMITATION_OF_LIABILITY":
                return "Sorumluluk limiti tanımlanmamış veya üst limit yok. Olası bir zararda sınırsız risk üstlenmiş oluyorsun.";
            case "TERMINATION":
                return "Karşı taraf hiçbir gerekçe göstermeden anlaşmayı feshedebiliyor. Gelir akışın aniden kesilebilir, fesih şartlarını pazarlık et.";
            case "CONFIDENTIALITY":
                return "Gizlilik yükümlülüğü çok tek taraflı olabilir. Sen bilgi sızdırırsan ağır yaptırım var ama karşı taraf için aynı seviyede değil olabilir.";
            case "GOVERNING_LAW":
                return "Uygulanacak hukuk senin lehine olmayabilir. Yabancı bir hukuk/mahkeme seçilmişse maliyet çok artar.";
            case "JURISDICTION":
                return "Uyuşmazlık halinde başka ülke/şehir mahkemeleri seçilmiş olabilir. Bu durumda dava açmak pratikte çok zorlaşır.";
            case "AUTO_RENEWAL":
                return "Sözleşme kendini otomatik uzatıyor olabilir. İptal etmezsen kilitlenebilirsin; yenileme şartlarını netleştir.";
            case "PAYMENT_TERMS":
                return "Ödeme süresi çok geç olabilir veya garanti yok olabilir. Nakit akışını bozabilir, ödeme güvence maddesi iste.";
            case "SLA":
                return "Hizmet seviyesini (uptime, response time vs.) açıkça garanti etmiyorsun ama ceza ödüyorsun olabilir. SLA net değilse ticari risk büyük.";
            case "DATA_PROTECTION":
                return "Kişisel veri koruması (KVKK / GDPR tipi) sana çok ağır sorumluluk yüklüyor olabilir. Veri ihlalinde tüm yük sende kalıyor.";
            case "INSURANCE_OBLIGATION":
                return "Belirli bir sigorta limitini zorunlu kılıyor olabilir (örneğin 1M$). Bu maliyeti gerçekten karşılayabiliyor musun, kontrol et.";
            case "INTELLECTUAL_PROPERTY_TRANSFER":
                return "Geliştirdiğin fikri mülkiyetin tüm haklarını karşı tarafa devrediyor olabilirsin. Bu ürünün değerini sıfırlar.";
            default:
                return "Bu madde ticari veya hukuki risk içeriyor. Müzakere edilmesi önerilir.";
        }
    }
}