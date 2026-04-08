package com.riskguard.service;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class TextExtractionService {

    private final Tika tika = new Tika();
    private final PdfTextExtractor pdfTextExtractor;

    public TextExtractionService(PdfTextExtractor pdfTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
    }

    /**
     * Geriye sadece metin döner (eski kullanım uyumluluğu için).
     */
    public String extract(InputStream in) throws Exception {
        ExtractedTextResult result = extractWithProvenance(in);
        return result.text;
    }

    /**
     * Yeni versiyon: hem metni hem de OCR kullanılıp kullanılmadığını döner.
     */
    public ExtractedTextResult extractWithProvenance(InputStream in) throws Exception {
        byte[] bytes = in.readAllBytes();
        String text = null;
        boolean usedOcr = false;

        // 1️⃣ Tika denemesi (Karakter Limiti Kaldırıldı)
        try (InputStream tikaIn = new ByteArrayInputStream(bytes)) {
            Metadata metadata = new Metadata();
            Parser parser = new AutoDetectParser();
            
            // DİKKAT: -1 parametresi ile Tika'nın 100.000 karakter okuma limiti sonsuz yapılır.
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            
            // tika.parseToString yerine daha güvenilir ve limitsiz olan parser.parse kullanıyoruz
            parser.parse(tikaIn, handler, metadata, context);
            String tikaText = handler.toString();

            if (tikaText != null) {
                tikaText = tikaText.trim();
            }

            if (tikaText != null && !tikaText.isBlank()) {
                text = tikaText;
                System.out.println("[TextExtractionService] Tika OK, length=" + text.length());
            } else {
                System.out.println("[TextExtractionService] Tika boş döndü, OCR fallback denenecek...");
            }
        } catch (NoSuchMethodError | NoSuchFieldError err) {
            System.err.println("[TextExtractionService] Tika sürüm uyumsuzluğu: " + err.getMessage());
        } catch (Throwable t) {
            System.err.println("[TextExtractionService] Tika parse hata verdi: "
                    + t.getClass().getName() + " -> " + t.getMessage());
        }

        // 2️⃣ OCR fallback (Hala text null ise, boşsa VEYA şüpheli derecede kısaysa)
        if (text == null || text.isBlank() || text.length() < 1500) {
            try {
                System.out.println("[TextExtractionService] Tika metni yetersiz buldu (" + 
                                  (text == null ? 0 : text.length()) + " karakter). OCR/PDFBox fallback çalışıyor...");
                
                InMemoryMultipartFile fakeFile = new InMemoryMultipartFile(
                        "upload",
                        "document.pdf",
                        "application/pdf",
                        bytes
                );

                String ocrText = pdfTextExtractor.extractText(fakeFile);
                if (ocrText == null) {
                    ocrText = "";
                }
                ocrText = ocrText.trim();

                // Eğer OCR, Tika'dan daha fazla metin bulduysa onu kabul et
                if (ocrText.length() > (text == null ? 0 : text.length())) {
                    text = ocrText;
                    usedOcr = true;
                    System.out.println("[TextExtractionService] OCR fallback OK, yeni uzunluk=" + text.length());
                } else {
                    System.out.println("[TextExtractionService] OCR da daha fazlasını bulamadı. Orijinal Tika metni korunuyor.");
                }
                
            } catch (Throwable t) {
                System.err.println("[TextExtractionService] OCR fallback da hata verdi: "
                        + t.getClass().getName() + " -> " + t.getMessage());
                usedOcr = true;
            }
        }

        return new ExtractedTextResult(text, usedOcr);
    }

    /**
     * Bellekteki byte[]'ı MultipartFile gibi davranan obje haline getiriyoruz.
     */
    private static class InMemoryMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name,
                              String originalFilename,
                              String contentType,
                              byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return this.name; }
        @Override public String getOriginalFilename() { return this.originalFilename; }
        @Override public String getContentType() { return this.contentType; }
        @Override public boolean isEmpty() { return this.content.length == 0; }
        @Override public long getSize() { return this.content.length; }
        @Override public byte[] getBytes() { return this.content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(this.content); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), this.content);
        }
    }
}