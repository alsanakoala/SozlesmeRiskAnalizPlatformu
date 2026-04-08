package com.riskguard.controller;

import com.riskguard.dto.RiskReportResponse;
import com.riskguard.entity.DocumentEntity;
import com.riskguard.repo.DocumentRepository;
import com.riskguard.service.AnalysisService;
import com.riskguard.service.DocumentService;
import com.riskguard.service.ReportRenderService;
import com.riskguard.service.PdfRenderService;
import com.riskguard.service.PdfRendererClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ui")
public class UiController {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private ReportRenderService reportRenderService;
    
    @Autowired
    private PdfRendererClient pdfRendererClient;

    @Autowired(required = false)
    private PdfRenderService pdfRenderService;

    @Value("${renderer.url:http://localhost:3005/render}")
    private String rendererUrl;

    @Value("${pdf.renderer.base-url:http://localhost:8080/}")
    private String renderBaseUrl;

    // 🔥 GÜVENLİK YARDIMCISI 1: Token'dan o anki kullanıcının E-postasını alır
    private String getCurrentUserEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String) {
            return (String) auth.getPrincipal();
        }
        return null;
    }

    // 🔥 GÜVENLİK YARDIMCISI 2: İstenen belgenin bu kullanıcıya ait olup olmadığını denetler (İzolasyon)
    private void checkOwnership(UUID documentId) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Belge bulunamadı"));
        
        String currentUser = getCurrentUserEmail();
        if (doc.getOwnerEmail() != null && currentUser != null && !doc.getOwnerEmail().equals(currentUser)) {
            throw new RuntimeException("🔒 Bu sözleşmeyi görüntüleme yetkiniz yok!");
        }
    }

    // ===========================================================
    // 📤 PDF UPLOAD & ANALYZE (HTML RAPOR DÖNER)
    // ===========================================================
    @PostMapping(
        path = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        try {
            DocumentEntity doc;
            try (InputStream in = file.getInputStream()) {
                doc = documentService.saveDocument(file.getOriginalFilename(), in);
            }

            // 🔥 SİHİRLİ DOKUNUŞ: Belgeyi yükleyen kullanıcıya zimmetle
            String email = getCurrentUserEmail();
            if (email != null) {
                doc.setOwnerEmail(email);
                documentRepository.save(doc); // Güncellemeyi kaydet
            }

            // analizi yap
            analysisService.analyze(doc.getId());
            RiskReportResponse report = analysisService.buildReport(doc.getId());

            String html = reportRenderService.renderHtml(report);

            Map<String, Object> payload = Map.of(
                "documentId", doc.getId().toString(),
                "html", html
            );

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(payload);

        } catch (Exception e) {
            String errHtml = "<html><body style='font-family:sans-serif;color:#b91c1c;background:#fff1f2;padding:16px'>" +
                "<h2>Analiz sırasında hata oluştu</h2><pre>" + safe(e.getMessage()) + "</pre></body></html>";
            return ResponseEntity.status(500).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", true, "html", errHtml));
        }
    }

    // ===========================================================
    // 📑 VAR OLAN RAPORU HTML OLARAK GETİR
    // ===========================================================
    @GetMapping(path = "/report/{documentId}", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getExistingReport(@PathVariable("documentId") UUID documentId) {
        try {
            checkOwnership(documentId); // 🔥 İZOLASYON KONTROLÜ
            RiskReportResponse report = analysisService.buildReport(documentId);
            String html = reportRenderService.renderHtml(report);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        } catch (Exception e) {
            return ResponseEntity.status(500).contentType(MediaType.TEXT_HTML)
                    .body("<h2>Erişim Reddedildi veya Hata</h2><pre>" + safe(e.getMessage()) + "</pre>");
        }
    }

    // ===========================================================
    // 🧾 PDF EXPORT (LEGACY / iText)
    // ===========================================================
    @GetMapping(path = "/report/{documentId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportReportPdf(@PathVariable("documentId") UUID documentId) {
        try {
            checkOwnership(documentId); // 🔥 İZOLASYON KONTROLÜ
            var report = analysisService.buildReport(documentId);
            var html   = reportRenderService.renderHtml(report);
            byte[] pdfBytes = pdfRendererClient.renderHtmlToPdf(html, renderBaseUrl);
            String downloadName = suggestDownloadName(report);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                    .contentType(MediaType.APPLICATION_PDF).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body(e.getMessage().getBytes());
        }
    }

    // ===========================================================
    // 🖨️ PDF EXPORT (HQ / Puppeteer)
    // ===========================================================
    @GetMapping(path = "/report/{documentId}/pdf-hq", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportReportPdfHq(@PathVariable("documentId") UUID documentId) {
        try {
            checkOwnership(documentId); // 🔥 İZOLASYON KONTROLÜ
            RiskReportResponse report = analysisService.buildReport(documentId);
            String html = reportRenderService.renderHtml(report);

            try {
                byte[] buf = renderWithRenderer(html);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + suggestDownloadName(report) + "\"")
                        .contentType(MediaType.APPLICATION_PDF).body(buf);
            } catch (Exception renderErr) {
                if (pdfRenderService == null) throw renderErr;
                byte[] fallback = pdfRenderService.renderPdf(html);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + suggestDownloadName(report) + "\"")
                        .contentType(MediaType.APPLICATION_PDF).body(fallback);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body(e.getMessage().getBytes());
        }
    }

    // ===========================================================
    // 📊 KATEGORİ BAZLI ORTALAMA SKORLAR (JSON)
    // ===========================================================
    @GetMapping(path = "/report/{documentId}/avg-scores", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Double>> getAvgScores(@PathVariable("documentId") UUID documentId) {
        checkOwnership(documentId); // 🔥 İZOLASYON KONTROLÜ
        RiskReportResponse report = analysisService.buildReport(documentId);
        return ResponseEntity.ok(report.avgScorePerCategory);
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String suggestDownloadName(RiskReportResponse report) {
        if (report != null && report.filename != null && !report.filename.isBlank()) {
            return report.filename.replaceAll("\\.[A-Za-z0-9]+$", "") + "-risk-report.pdf";
        }
        return "risk-report.pdf";
    }

    private byte[] renderWithRenderer(String html) throws Exception {
        String payload = """
            { "html": %s, "baseUrl": "http://localhost:8080/", "waitUntil": "networkidle0", "timeoutMs": 45000, "viewport": { "width": 1280, "height": 1000, "deviceScaleFactor": 1 }, "pdf": { "format": "A4", "margin": { "top": "12mm", "right": "12mm", "bottom": "14mm", "left": "12mm" }, "printBackground": true, "preferCSSPageSize": true, "displayHeaderFooter": true, "headerTemplate": "<div style=\\"font-size:10px;width:100%;padding:4px 8px;color:#6b7280;font-family:Arial,sans-serif;\\"></div>", "footerTemplate": "<div style=\\"font-size:10px;width:100%;padding:4px 8px;color:#6b7280;font-family:Arial,sans-serif;display:flex;justify-content:space-between;\\"><span class=\\"pageNumber\\"></span>/<span class=\\"totalPages\\"></span></div>" } }
            """.formatted(jsonString(html));

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(rendererUrl))
                .timeout(Duration.ofSeconds(60)).header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();

        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) return resp.body();
        throw new IllegalStateException("Renderer HTTP " + resp.statusCode() + " döndü");
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 64);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}