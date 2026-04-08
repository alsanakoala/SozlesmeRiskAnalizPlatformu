package com.riskguard.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringJoiner;

/**
 * OCR tabanlı metin çıkarıcı.
 * Bu sürüm pdfbox 3.x ile uyumludur.
 * - Önce İngilizce ("eng")
 * - Sonra Türkçe ("tur") deniyor.
 * Tesseract dil verilerini bulamazsa veya native hata atarsa,
 * boş string döndürür ama uygulamanın çökmesini engeller.
 */
@Service
public class OcrTextExtractor {

    /**
     * Tesseract instance hazırlar.
     * TESSDATA_PREFIX ortam değişkeni tanımlıysa onu kullanır.
     */
    private Tesseract newTesseract(String lang) {
        Tesseract t = new Tesseract();
        t.setLanguage(lang); // "eng", "tur" vs.

        // Tesseract dil dosyaları için güvenli datapath ayarı:
        // Sen daha önce bunu env ile veriyordun:
        // TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata
        String tessDataPrefix = System.getenv("TESSDATA_PREFIX");
        if (tessDataPrefix != null && !tessDataPrefix.isBlank()) {
            t.setDatapath(tessDataPrefix);
        }

        // İstersen burada whitelist/psm/dpi ayarları da yapılabilir.
        return t;
    }

    /**
     * Belirli bir dil için tüm sayfaları OCR eder.
     */
    private String runOcr(PDFRenderer renderer, int pageCount, String lang) throws IOException {
    // Artık newTesseract(lang) kullanmıyoruz.
    // Tesseract'ı burada elle kuruyoruz ki TESSDATA_PREFIX'e ihtiyaç olmasın.
    Tesseract t = new Tesseract();

    // Dil (eng / tur)
    t.setLanguage(lang);

    // Dil data path'ini elle veriyoruz. Böylece mvn spring-boot:run yaparken
    // ekstra environment variable set etmene gerek kalmaz.
    // Bu path Ubuntu'da tesseract-ocr paketinin traineddata dosyalarının default konumu.
    t.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");

    StringJoiner joiner = new StringJoiner("\n\n");

    for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
        try {
            /*
             * pdfbox 3.x'te renderImage API'si değişti:
             * - eski: renderImageWithDPI(pageIndex, 300, ImageType.RGB)
             * - yeni: renderImage(pageIndex, scale, ImageType, RenderDestination)
             *
             * scale = 300 / 72 ≈ 4.166..., 72 dpi base kabul edilir.
             * Biz 4.17 gibi bir ölçek kullanalım ki yaklaşık 300 dpi olsun.
             */
            float scale = 300f / 72f;

            BufferedImage pageImage = renderer.renderImage(
                    pageIndex,
                    scale,
                    ImageType.RGB,
                    RenderDestination.EXPORT  // yüksek kalite render
            );

            String pageText = t.doOCR(pageImage);

            if (pageText != null && !pageText.isBlank()) {
                joiner.add(pageText.trim());
            }

        } catch (TesseractException te) {
            System.err.println(
                    "[OCR] TesseractException (" + lang + ") page " + pageIndex + ": " + te.getMessage()
            );
        } catch (UnsatisfiedLinkError nativeCrash) {
            // tess4j bazen native lib yüzünden JVM patlatabiliyor.
            // Bunu yakalayıp güvenli şekilde çıkıyoruz.
            System.err.println(
                    "[OCR] Native OCR error (" + lang + ") page " + pageIndex + ": " + nativeCrash.getMessage()
            );
            // Bu durumda daha fazla sayfayı zorlamanın anlamı yok, kır.
            break;
        } catch (Throwable any) {
            // Güvenlik ağı: hiçbir şekilde tüm request'i patlatma
            System.err.println(
                    "[OCR] Unexpected OCR error (" + lang + ") page " + pageIndex + ": " + any.getClass().getName()
                            + " -> " + any.getMessage()
            );
        }
    }

    return joiner.toString().trim();
}


    /**
     * Ana giriş noktası.
     * 1) PDF'i belleğe yükler.
     * 2) İngilizce OCR dener.
     * 3) Boşsa Türkçe OCR dener.
     * 4) İkisi de boşsa "" döner.
     *
     * Eğer OCR sırasında bir şey patlarsa asla exception fırlatmayız; "" döneriz.
     * Böylece Controller tarafı 500 ile ölmez.
     */
    public String extractWithOcr(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             PDDocument document = Loader.loadPDF(in.readAllBytes())) {

            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            // Önce İngilizce dene
            String engResult = runOcr(renderer, pageCount, "eng");
            if (engResult != null && !engResult.isBlank()) {
                return engResult;
            }

            // Sonra Türkçe dene
            String turResult = runOcr(renderer, pageCount, "tur");
            if (turResult != null && !turResult.isBlank()) {
                return turResult;
            }

            return "";

        } catch (IOException io) {
            System.err.println("[OCR] IO error while reading PDF: " + io.getMessage());
            return "";
        } catch (Throwable t) {
            // Son güvenlik ağı.
            // Özellikle native taraf çökmesin diye burada swallow ediyoruz.
            System.err.println("[OCR] Fatal OCR error: " + t.getClass().getName() + " -> " + t.getMessage());
            return "";
        }
    }
}
