package com.riskguard.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// (Legacy fallback için) openhtmltopdf importları:
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

// >>> (Legacy) font için importlar (fallback'te lazım olabilir)
import org.xhtmlrenderer.pdf.ITextFontResolver;
import com.lowagie.text.pdf.BaseFont;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
// <<<

@Service
public class PdfRenderService {

    // ======= KONFİG =======
    @Value("${riskguard.renderer.url:http://localhost:3005/render}")
    private String rendererUrl; // Puppeteer servis endpoint

    @Value("${riskguard.renderer.token:}")
    private String rendererToken; // İstersen server.js tarafında ALLOW_TOKEN ile eşle

    @Value("${server.publicBaseUrl:http://localhost:8080/}")
    private String publicBaseUrl; // asset kökü (css/img/font) — UI ile aynı origin

    // ======= KAMU METODU =======
    public byte[] renderPdf(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("Rapor HTML içeriği boş geldi!");
        }

        try {
            // 1) ÖNCE CHROMIUM (Puppeteer) İLE DENE — Önizleme ile birebir görünüm
            return renderPdfWithChromium(html, publicBaseUrl);

        } catch (Exception chromiumErr) {
            System.err.println("[PdfRenderService] Chromium render hatası: " + chromiumErr.getMessage());
            tryDump("chromium", html, chromiumErr);

            // 2) FALLBACK: openhtmltopdf (eski yol) — zorunlu değil, istersen kalıcı olarak kaldırırız
            try {
                return renderPdfOpenHtmlTopdf(html);
            } catch (Exception legacyErr) {
                System.err.println("[PdfRenderService] Fallback (openhtmltopdf) da hataya düştü: " + legacyErr.getMessage());
                tryDump("openhtmltopdf", html, legacyErr);
                throw new RuntimeException("PDF render edilemedi (Chromium ve fallback başarısız).", legacyErr);
            }
        }
    }

    // ======= 1) CHROMIUM RENDERER (PREFERRED) =======
    private byte[] renderPdfWithChromium(String rawHtml, String baseUrl) throws Exception {
        // Puppeteer servisine JSON POST
        String payload = """
            {
              "html": %s,
              "baseUrl": %s,
              "waitUntil": "networkidle0",
              "timeoutMs": 45000,
              "viewport": {"width":1280,"height":1000,"deviceScaleFactor":1},
              "pdf": {
                "format": "A4",
                "margin": {"top":"12mm","right":"12mm","bottom":"14mm","left":"12mm"},
                "printBackground": true,
                "preferCSSPageSize": true
              }
            }
            """.formatted(
                jsonString(rawHtml),
                jsonString(baseUrl == null ? "http://localhost:8080/" : baseUrl)
            );

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(rendererUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

        if (rendererToken != null && !rendererToken.isBlank()) {
            builder.header("Authorization", "Bearer " + rendererToken);
        }

        HttpRequest req = builder.build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return resp.body(); // PDF bytes
        }

        throw new IllegalStateException("Chromium renderer HTTP " + resp.statusCode() + " döndürdü");
    }

    // ======= 2) FALLBACK: OPENHTMLTOPDF (ESKİ YÖNTEM) =======
    private byte[] renderPdfOpenHtmlTopdf(String html) throws Exception {
        // NOTE: Chromium rendering varken bu yol gereksiz; ama şimdilik bırakıyoruz.
        // İstersen bu metodu ve alttaki yardımcıları tamamen silebiliriz.
        String safeHtml = new String(html.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        safeHtml = preEscapeForXml(safeHtml);
        safeHtml = stripInvalidXmlChars(safeHtml);
        String xhtmlBody = toXhtmlBody(safeHtml);
        String sanitizedHtml = wrapAsXhtmlDocument(xhtmlBody);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();

        // (fallback) TTF font göm
        try {
            ITextFontResolver fr = renderer.getFontResolver();
            String dejavuRegular = ensureFontTemp("DejaVuSans.ttf");
            String dejavuBold    = ensureFontTemp("DejaVuSans-Bold.ttf");
            String dejavuMono    = ensureFontTemp("DejaVuSansMono.ttf");

            fr.addFont(dejavuRegular, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            fr.addFont(dejavuBold,    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            fr.addFont(dejavuMono,    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            System.out.println("[PdfRenderService] (fallback) DejaVu fontları kaydedildi.");
        } catch (Exception fe) {
            System.err.println("[PdfRenderService] (fallback) Font kayıt hatası: " + fe.getMessage());
        }

        renderer.setDocumentFromString(sanitizedHtml, "file:///"); // base URL
        renderer.layout();
        renderer.createPDF(baos);
        return baos.toByteArray();
    }

    // ======= Yardımcılar (fallback için) =======
    private String preEscapeForXml(String s) {
        s = s.replaceAll("&(?!([a-zA-Z]{2,8}|#\\d{1,7}|#x[0-9A-Fa-f]{1,6});)", "&amp;");
        s = s
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '\"')
            .replace('\u201D', '\"')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u00A0', ' ');
        return s;
    }

    private String stripInvalidXmlChars(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == 0x9 || ch == 0xA || ch == 0xD ||
                (ch >= 0x20 && ch <= 0xD7FF) ||
                (ch >= 0xE000 && ch <= 0xFFFD)) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private String toXhtmlBody(String html) {
        var safelist = Safelist.relaxed()
                .addTags("table","thead","tbody","tfoot","tr","th","td","colgroup","col")
                .addAttributes(":all", "style", "class", "id", "width", "height", "colspan", "rowspan", "align");

        String cleaned = Jsoup.clean(html, safelist);

        var doc = Jsoup.parseBodyFragment(cleaned);
        OutputSettings os = new OutputSettings();
        os.syntax(OutputSettings.Syntax.xml);
        os.escapeMode(Entities.EscapeMode.xhtml);
        os.charset(StandardCharsets.UTF_8);
        doc.outputSettings(os);

        return doc.body().html();
    }

    private String wrapAsXhtmlDocument(String xhtmlBodyInner) {
        StringBuilder sb = new StringBuilder(1024 + xhtmlBodyInner.length());
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"tr\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\"/>\n");
        sb.append("  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"/>\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n");
        sb.append("  <style type=\"text/css\">\n");
        sb.append("    @page{size:A4;margin:12mm 12mm 14mm 12mm;}\n");
        sb.append("    html, body { font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 13px; line-height: 1.55; color:#111; }\n");
        sb.append("    img { max-width: 100%; }\n");
        sb.append("    table { border-collapse: collapse; width: 100%; }\n");
        sb.append("    th, td { border: 1px solid #ddd; padding: 6px; }\n");
        sb.append("    .ui-inline-pdf-btn, .pdf-btn, .ai-suggestion { display: none !important; }\n");
        sb.append("    .emoji, .icon { display:none !important; }\n");
        sb.append("    .card, .snippet, table, thead, tbody, tr, td, th { page-break-inside: avoid; break-inside: avoid; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append(xhtmlBodyInner);
        sb.append("\n</body>\n</html>");
        return sb.toString();
    }

    private String ensureFontTemp(String fileName) throws Exception {
        String resourcePath = "/fonts/" + fileName;
        try (InputStream is = PdfRenderService.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Font kaynağı bulunamadı: " + resourcePath + " (resources/fonts altına koyun)");
            }
            Path out = Paths.get(System.getProperty("java.io.tmpdir"), "rg-font-" + fileName);
            if (!Files.exists(out) || Files.size(out) == 0) {
                Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
            }
            return out.toAbsolutePath().toString();
        }
    }

    // ======= Ortak yardımcılar =======
    private void tryDump(String tag, String html, Exception ex) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File f = new File("/tmp/rg-report-" + tag + "-" + ts + ".html");
            try (FileWriter fw = new FileWriter(f, StandardCharsets.UTF_8)) {
                fw.write("<!doctype html><meta charset='utf-8'><pre style='white-space:pre-wrap;font-family:monospace'>\n");
                fw.write("PDF render hatası (" + tag + "): " + ex.getClass().getName() + " - " + ex.getMessage() + "\n\n");
                fw.write("Kaynak HTML (ilk 50KB):\n\n");
                String cut = html.length() > 50_000 ? html.substring(0, 50_000) + "\n...[trimmed]..." : html;
                fw.write(cut);
                fw.write("</pre>");
            }
            System.err.println("[PdfRenderService] Problemli HTML dump: " + f.getAbsolutePath());
        } catch (Exception ignore) {}
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        // basit kaçış
        String esc = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + esc + "\"";
    }
}
