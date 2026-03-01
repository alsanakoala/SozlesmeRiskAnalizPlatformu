package com.riskguard.service;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfTextExtractor {

    private final Tika tika = new Tika();
    private final OcrTextExtractor ocrTextExtractor;

    public PdfTextExtractor(OcrTextExtractor ocrTextExtractor) {
        this.ocrTextExtractor = ocrTextExtractor;
    }

    public String extractText(MultipartFile file) {
        String text = null;

        // 1. Önce Tika'yı dene
        try {
            System.out.println("[PdfTextExtractor] Tika deniyorum...");
            Metadata metadata = new Metadata();
            text = tika.parseToString(file.getInputStream(), metadata);

            if (text != null) {
                text = text.trim();
            }

            // Eğer Tika başarılıysa ve boş değilse direkt dön
            if (text != null && !text.isBlank()) {
                System.out.println("[PdfTextExtractor] Tika OK, length=" + text.length());
                return text;
            } else {
                System.out.println("[PdfTextExtractor] Tika metin bulamadı (boş döndü). OCR'e geçiyoruz...");
            }

        } catch (NoSuchMethodError err) {
            // pdfbox sürüm uyuşmazlığı gibi düşük seviye hata
            System.err.println("[PdfTextExtractor] Tika PDF parser versiyon uyuşmazlığı (NoSuchMethodError): " + err.getMessage());
            // devam edip OCR'e geçeceğiz
        } catch (NoSuchFieldError err) {
            System.err.println("[PdfTextExtractor] Tika PDF parser versiyon uyuşmazlığı (NoSuchFieldError): " + err.getMessage());
        } catch (Throwable t) {
            // burada Exception + Error hepsini yakalıyoruz ki request 500 olmasın
            System.err.println("[PdfTextExtractor] Tika parse hata verdi: " + t.getClass().getName() + " -> " + t.getMessage());
        }

        // 2. OCR fallback
        System.out.println("[PdfTextExtractor] OCR fallback çalışıyor...");
        String ocrText = ocrTextExtractor.extractWithOcr(file);

        if (ocrText == null) {
            ocrText = "";
        }
        ocrText = ocrText.trim();

        System.out.println("[PdfTextExtractor] OCR bitti, length=" + ocrText.length());

        return ocrText;
    }
}
