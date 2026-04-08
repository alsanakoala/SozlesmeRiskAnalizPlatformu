package com.riskguard.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

@Service
public class PdfTextExtractor {

    private final OcrTextExtractor ocrTextExtractor;

    public PdfTextExtractor(OcrTextExtractor ocrTextExtractor) {
        this.ocrTextExtractor = ocrTextExtractor;
    }

    public String extractText(MultipartFile file) {
        String text = "";

        // 1. ŞİMŞEK HIZI: PDFBox ile doğrudan saf metin okuma (Milisaniyeler sürer)
        try {
            System.out.println("[PdfTextExtractor] PDFBox ile saf metin çıkarımı deneniyor...");
            try (InputStream in = file.getInputStream();
                 PDDocument document = Loader.loadPDF(in.readAllBytes())) {

                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(document);
            }

            if (text != null) {
                text = text.trim();
            }

            // Eğer PDFBox metni başarıyla bulduysa, o ağır OCR işlemine ASLA girme!
            if (text != null && text.length() > 50) {
                System.out.println("[PdfTextExtractor] PDFBox BAŞARILI, length=" + text.length());
                return text;
            } else {
                System.out.println("[PdfTextExtractor] PDFBox metin bulamadı (Belki taranmış bir resim). OCR'e geçiliyor...");
            }

        } catch (Throwable t) {
            System.err.println("[PdfTextExtractor] PDFBox hata verdi: " + t.getMessage());
        }

        // 2. ZORUNLU DURUM (FALLBACK): Sadece PDF gerçekten bir fotoğrafsa OCR çalışır
        System.out.println("[PdfTextExtractor] OCR fallback çalışıyor...");
        String ocrText = ocrTextExtractor.extractWithOcr(file);

        if (ocrText == null) {
            ocrText = "";
        }
        
        System.out.println("[PdfTextExtractor] OCR Bitti, length=" + ocrText.length());
        return ocrText.trim();
    }
}