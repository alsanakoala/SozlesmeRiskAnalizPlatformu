package com.riskguard.service;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
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

        // 1️⃣ Tika denemesi
        try (InputStream tikaIn = new ByteArrayInputStream(bytes)) {
            Metadata metadata = new Metadata();
            String tikaText = tika.parseToString(tikaIn, metadata);

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

        // 2️⃣ OCR fallback (hala text null veya blank ise)
        if (text == null || text.isBlank()) {
            try {
                System.out.println("[TextExtractionService] OCR fallback çalışıyor...");
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

                text = ocrText;
                usedOcr = true;
                System.out.println("[TextExtractionService] OCR fallback OK, length=" + text.length());
            } catch (Throwable t) {
                System.err.println("[TextExtractionService] OCR fallback da hata verdi: "
                        + t.getClass().getName() + " -> " + t.getMessage());
                text = "";
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
