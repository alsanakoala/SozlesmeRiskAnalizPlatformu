package com.riskguard.controller;

import com.riskguard.dto.AnalyzeResponse;
import com.riskguard.dto.RiskFindingDTO;
import com.riskguard.dto.RiskReportResponse;
import com.riskguard.entity.ClauseEntity;
import com.riskguard.entity.DocumentEntity;
import com.riskguard.repo.DocumentRepository;
import com.riskguard.repo.ClauseRepository;
import com.riskguard.repo.RiskFindingRepository;
import com.riskguard.service.AnalysisService;
import com.riskguard.service.ClauseSegmentationService;
import com.riskguard.service.DocumentService;
import com.riskguard.service.PdfRenderService;
import com.riskguard.service.PdfTextExtractor;
import com.riskguard.service.ReportRenderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;
    private final AnalysisService analysisService;

    private final DocumentRepository documentRepository;
    private final ClauseRepository clauseRepository;
    private final RiskFindingRepository findingRepository;

    private final ReportRenderService reportRenderService;
    private final PdfRenderService pdfRenderService;

    private final PdfTextExtractor pdfTextExtractor;
    private final ClauseSegmentationService clauseSegmentationService;

    public DocumentController(
            DocumentService documentService,
            AnalysisService analysisService,
            DocumentRepository documentRepository,
            ClauseRepository clauseRepository,
            RiskFindingRepository findingRepository,
            ReportRenderService reportRenderService,
            PdfRenderService pdfRenderService,
            PdfTextExtractor pdfTextExtractor,
            ClauseSegmentationService clauseSegmentationService
    ) {
        this.documentService = documentService;
        this.analysisService = analysisService;
        this.documentRepository = documentRepository;
        this.clauseRepository = clauseRepository;
        this.findingRepository = findingRepository;
        this.reportRenderService = reportRenderService;
        this.pdfRenderService = pdfRenderService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.clauseSegmentationService = clauseSegmentationService;
    }

    // basit health (actuator yoksa burası var)
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        return body;
    }

    // ==========================
    //  DİL TESPİTİ (helper)
    // ==========================
    private String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }

        String lower = text.toLowerCase(Locale.ROOT);

        int turkishHits = 0;
        if (lower.contains("ş")) turkishHits++;
        if (lower.contains("ğ")) turkishHits++;
        if (lower.contains("ı")) turkishHits++;
        if (lower.contains("ç")) turkishHits++;
        if (lower.contains("ö")) turkishHits++;
        if (lower.contains("ü")) turkishHits++;

        if (lower.contains("taraflar")) turkishHits++;
        if (lower.contains("yürürlük")) turkishHits++;
        if (lower.contains("fesih")) turkishHits++;
        if (lower.contains("yükümlülük")) turkishHits++;
        if (lower.contains("kişisel veri")) turkishHits++;
        if (lower.contains("gizlilik")) turkishHits++;

        int englishHits = 0;
        if (lower.contains("party") || lower.contains("parties")) englishHits++;
        if (lower.contains("governing law")) englishHits++;
        if (lower.contains("liability")) englishHits++;
        if (lower.contains("indemnify") || lower.contains("indemnification")) englishHits++;
        if (lower.contains("confidentiality")) englishHits++;
        if (lower.contains("termination")) englishHits++;
        if (lower.contains("personal data")) englishHits++;

        if (turkishHits == 0 && englishHits == 0) {
            if (lower.matches(".*[çğıöşü].*")) {
                return "tr";
            }
            return "en";
        }

        if (turkishHits >= englishHits) {
            return "tr";
        } else {
            return "en";
        }
    }

    // ======================================
    // 1. YÜKLE + ANALİZ + CEVAP DÖN (JSON)
    // ======================================
    @PostMapping(
            value = "/documents/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DocumentUploadFullResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {

        System.out.println("=== [UPLOAD] yeni pipeline ÇAĞRILDI ===");
        System.out.println("dosya adı: " + file.getOriginalFilename());

        // 1. PDF -> text
        String extractedText = pdfTextExtractor.extractText(file);
        if (extractedText == null) extractedText = "";
        System.out.println("extractedText length = " + extractedText.length());

        // 2. DocumentEntity oluştur/kaydet (ilk hali)
        DocumentEntity doc;
        try (var in = file.getInputStream()) {
            // saveDocument muhtemelen yeni bir DocumentEntity oluşturup id veriyor
            doc = documentService.saveDocument(file.getOriginalFilename(), in);
        }

        // alanları doldur
        String detectedLang = detectLanguage(extractedText);
        doc.setLanguage(detectedLang);
        doc.setText(extractedText);
        doc.setUploadedAt(Instant.now());

        // DB'ye yaz (persist güncellemesi)
        doc = documentRepository.save(doc);

        UUID documentId = doc.getId();

        // 3. clause segmentation -> ClauseEntity kayıt
        List<ClauseSegmentationService.ClauseSegment> clauseBlocks =
                clauseSegmentationService.splitIntoClausesWithOffsets(extractedText);

        int idx = 0;
        for (ClauseSegmentationService.ClauseSegment seg : clauseBlocks) {
            ClauseEntity clause = new ClauseEntity();
            clause.setDocumentId(documentId);
            clause.setIdx(idx++);
            clause.setHeading(null);
            clause.setText(seg.text);
            clause.setSpanStart(seg.start);
            clause.setSpanEnd(seg.end);
            clauseRepository.save(clause);
        }

        // 4. risk analizi tetikle
        AnalyzeResponse analysisResult = analysisService.analyze(documentId);

        // en yüksek skorlu ilk 3 bulgu
        List<RiskFindingDTO> topFindings = analysisResult.getFindings()
                .stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(3)
                .collect(Collectors.toList());

        // 5. response DTO hazırla
        DocumentUploadFullResponse resp = new DocumentUploadFullResponse();
        resp.id = documentId;
        resp.filename = doc.getFilename();
        resp.language = doc.getLanguage();
        resp.textPreview = preview(doc.getText());
        resp.uploadedAt = doc.getUploadedAt();
        resp.totalScore = analysisResult.getTotalScore();
        resp.topFindings = topFindings;

        resp.reportHtmlUrl = "/api/v1/documents/" + documentId + "/reportHtml";
        resp.reportPdfUrl  = "/api/v1/documents/" + documentId + "/reportPdf";

        // JSON olarak dön
        return ResponseEntity.ok(resp);
    }

    private String preview(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500) + "...";
    }

    // Manuel tekrar analiz etmek istersek
    @PostMapping("/documents/{id}/analyze")
    public AnalyzeResponse analyze(@PathVariable("id") UUID id) {
        return analysisService.analyze(id);
    }

    // Tekil dokümanı getir
    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocument(@PathVariable("id") UUID id) {
        return documentRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Segmentlenmiş maddeleri getir
    @GetMapping("/documents/{id}/clauses")
    public List<Map<String, Object>> getClauses(@PathVariable("id") UUID id) {
        return clauseRepository.findByDocumentIdOrderByIdxAsc(id).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("idx", c.getIdx());
                    m.put("heading", c.getHeading());
                    m.put("text", c.getText());
                    m.put("spanStart", c.getSpanStart());
                    m.put("spanEnd", c.getSpanEnd());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // Risk bulgularını getir
    @GetMapping("/documents/{id}/findings")
    public List<RiskFindingDTO> getFindings(@PathVariable("id") UUID id) {
        return findingRepository.findByDocumentId(id).stream().map(f -> {
            RiskFindingDTO d = new RiskFindingDTO();
            d.id = f.getId();
            d.clauseId = f.getClauseId();
            d.category = f.getCategory();
            d.ruleId = f.getRuleId();
            d.score = f.getScore();
            d.confidence = f.getConfidence();
            d.snippet = f.getSnippet();
            d.explanation = f.getExplanation();
            d.mitigation = f.getMitigation(); // mitigation alanını da dönelim
            return d;
        }).collect(Collectors.toList());
    }

    // JSON rapor
    @GetMapping("/documents/{id}/report")
    public ResponseEntity<?> getReport(@PathVariable("id") UUID id) {
        try {
            RiskReportResponse report = analysisService.buildReport(id);
            return ResponseEntity.ok(report);
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // HTML rapor
    @GetMapping(
            value = "/documents/{id}/reportHtml",
            produces = "text/html; charset=UTF-8"
    )
    public ResponseEntity<String> getReportHtml(@PathVariable("id") UUID id) {
        try {
            RiskReportResponse report = analysisService.buildReport(id);
            String html = reportRenderService.renderHtml(report);
            return ResponseEntity.ok(html);
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // PDF rapor (fallback'li)
    @GetMapping("/documents/{id}/reportPdf")
    public ResponseEntity<byte[]> getReportPdf(@PathVariable("id") UUID id) {
        try {
            RiskReportResponse report = analysisService.buildReport(id);
            String html = reportRenderService.renderHtml(report);

            try {
                byte[] pdfBytes = pdfRenderService.renderPdf(html);

                return ResponseEntity.ok()
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"risk_report_" + id + ".pdf\""
                        )
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdfBytes);

            } catch (Throwable pdfErr) {
                System.err.println("[PDF EXPORT ERROR] " + pdfErr.getClass().getName() + ": " + pdfErr.getMessage());

                String fallbackText =
                        "PDF export sırasında hata oluştu.\n" +
                        "Lütfen /api/v1/documents/" + id + "/reportHtml çıktısını " +
                        "tarayıcıdan 'Print to PDF' ile kaydedin.\n";

                byte[] fallbackBytes = fallbackText.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                return ResponseEntity.ok()
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"risk_report_" + id + "_FAILED.pdf\""
                        )
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(fallbackBytes);
            }

        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // === Upload sonrası dönen JSON DTO ===
    public static class DocumentUploadFullResponse {
        public UUID id;
        public String filename;
        public String language;
        public String textPreview;
        public Instant uploadedAt;
        public double totalScore;
        public List<RiskFindingDTO> topFindings;

        public String reportHtmlUrl;
        public String reportPdfUrl;
    }
}
