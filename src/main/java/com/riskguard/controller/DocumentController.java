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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") 
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

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
            DocumentService documentService, AnalysisService analysisService, DocumentRepository documentRepository,
            ClauseRepository clauseRepository, RiskFindingRepository findingRepository, ReportRenderService reportRenderService,
            PdfRenderService pdfRenderService, PdfTextExtractor pdfTextExtractor, ClauseSegmentationService clauseSegmentationService
    ) {
        this.documentService = documentService; this.analysisService = analysisService; this.documentRepository = documentRepository;
        this.clauseRepository = clauseRepository; this.findingRepository = findingRepository; this.reportRenderService = reportRenderService;
        this.pdfRenderService = pdfRenderService; this.pdfTextExtractor = pdfTextExtractor; this.clauseSegmentationService = clauseSegmentationService;
    }

    // 🔥 GÜVENLİK YARDIMCISI
    private String getCurrentUserEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String) {
            return (String) auth.getPrincipal();
        }
        return null;
    }

    private void verifyOwnership(UUID documentId) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Belge bulunamadı"));
        String currentUser = getCurrentUserEmail();
        if (doc.getOwnerEmail() != null && currentUser != null && !doc.getOwnerEmail().equals(currentUser)) {
            throw new RuntimeException("🔒 Unauthorized access to this document");
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    private String detectLanguage(String text) {
        if (text == null || text.isBlank()) return "unknown";
        String lower = text.toLowerCase(Locale.ROOT);
        int trHits = 0, enHits = 0;
        if(lower.matches(".*[şğıçöü].*")) trHits+=3;
        if(lower.contains("taraflar") || lower.contains("fesih")) trHits++;
        if(lower.contains("party") || lower.contains("liability")) enHits++;
        return (trHits >= enHits) ? "tr" : "en";
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentUploadFullResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
        String extractedText = pdfTextExtractor.extractText(file);
        if (extractedText == null) extractedText = "";
        
        DocumentEntity doc;
        try (var in = file.getInputStream()) {
            doc = documentService.saveDocument(file.getOriginalFilename(), in);
        }

        doc.setLanguage(detectLanguage(extractedText));
        doc.setText(extractedText);
        doc.setUploadedAt(Instant.now());

        // 🔥 YENİ EKLENDİ: Yükleyen kullanıcının e-postasını belgeye zimmetle
        String email = getCurrentUserEmail();
        if (email != null) {
            doc.setOwnerEmail(email);
        }

        doc = documentRepository.save(doc);
        UUID documentId = doc.getId();

        List<ClauseSegmentationService.ClauseSegment> clauseBlocks = clauseSegmentationService.splitIntoClausesWithOffsets(extractedText);
        int idx = 0;
        for (ClauseSegmentationService.ClauseSegment seg : clauseBlocks) {
            ClauseEntity clause = new ClauseEntity();
            clause.setDocumentId(documentId);
            clause.setIdx(idx++);
            clause.setText(seg.text);
            clause.setSpanStart(seg.start);
            clause.setSpanEnd(seg.end);
            clauseRepository.save(clause);
        }

        AnalyzeResponse analysisResult = analysisService.analyze(documentId);
        List<RiskFindingDTO> topFindings = analysisResult.getFindings().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())).limit(3).collect(Collectors.toList());

        DocumentUploadFullResponse resp = new DocumentUploadFullResponse();
        resp.id = documentId; resp.filename = doc.getFilename(); resp.language = doc.getLanguage();
        resp.textPreview = extractedText.length() > 500 ? extractedText.substring(0, 500) + "..." : extractedText;
        resp.uploadedAt = doc.getUploadedAt(); resp.totalScore = analysisResult.getTotalScore();
        resp.topFindings = topFindings;
        resp.reportHtmlUrl = "/api/v1/documents/" + documentId + "/reportHtml";
        resp.reportPdfUrl  = "/api/v1/documents/" + documentId + "/reportPdf";

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/documents/{id}/analyze")
    public AnalyzeResponse analyze(@PathVariable("id") UUID id) {
        verifyOwnership(id); // 🔥 İzolasyon
        return analysisService.analyze(id);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocument(@PathVariable("id") UUID id) {
        try { verifyOwnership(id); } catch(Exception e) { return ResponseEntity.status(403).body(e.getMessage()); }
        return documentRepository.findById(id).<ResponseEntity<?>>map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/documents/{id}/clauses")
    public ResponseEntity<?> getClauses(@PathVariable("id") UUID id) {
        try { verifyOwnership(id); } catch(Exception e) { return ResponseEntity.status(403).body(e.getMessage()); }
        return ResponseEntity.ok(clauseRepository.findByDocumentIdOrderByIdxAsc(id));
    }

    @GetMapping("/documents/{id}/findings")
    public ResponseEntity<?> getFindings(@PathVariable("id") UUID id) {
        try { verifyOwnership(id); } catch(Exception e) { return ResponseEntity.status(403).body(e.getMessage()); }
        return ResponseEntity.ok(findingRepository.findByDocumentId(id));
    }

    @GetMapping("/documents/{id}/report")
    public ResponseEntity<?> getReport(@PathVariable("id") UUID id) {
        try {
            verifyOwnership(id); // 🔥 İzolasyon
            return ResponseEntity.ok(analysisService.buildReport(id));
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/documents/{id}/reportHtml", produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> getReportHtml(@PathVariable("id") UUID id) {
        try {
            verifyOwnership(id); // 🔥 İzolasyon
            return ResponseEntity.ok(reportRenderService.renderHtml(analysisService.buildReport(id)));
        } catch (Exception ex) {
            return ResponseEntity.status(403).body("Unauthorized or Not Found");
        }
    }

    @GetMapping("/documents/{id}/reportPdf")
    public ResponseEntity<byte[]> getReportPdf(@PathVariable("id") UUID id) {
        try {
            verifyOwnership(id); // 🔥 İzolasyon
            String html = reportRenderService.renderHtml(analysisService.buildReport(id));
            byte[] pdfBytes = pdfRenderService.renderPdf(html);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"risk_report_" + id + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF).body(pdfBytes);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(ex.getMessage().getBytes());
        }
    }

    public static class DocumentUploadFullResponse {
        public UUID id; public String filename; public String language; public String textPreview;
        public Instant uploadedAt; public double totalScore; public List<RiskFindingDTO> topFindings;
        public String reportHtmlUrl; public String reportPdfUrl;
    }

    // ===========================================================
    // 🗂️ GEÇMİŞ SÖZLEŞMELERİM (Kullanıcının Kendi Belgeleri)
    // ===========================================================
    @GetMapping("/documents/my")
    public ResponseEntity<List<DocumentSummaryDTO>> getMyDocuments() {
        String email = getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(401).build();
        }

        // Veritabanından kullanıcının belgelerini çek
        List<DocumentEntity> myDocs = documentRepository.findByOwnerEmailOrderByUploadedAtDesc(email);

        // İhtiyacımız olan özet bilgileri DTO'ya çevir
        List<DocumentSummaryDTO> result = myDocs.stream().map(doc -> {
            DocumentSummaryDTO dto = new DocumentSummaryDTO();
            dto.id = doc.getId();
            dto.filename = doc.getFilename();
            dto.language = doc.getLanguage();
            dto.uploadedAt = doc.getUploadedAt();
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Geçmiş belgeleri listelemek için hafif bir DTO (Data Transfer Object)
    public static class DocumentSummaryDTO {
        public UUID id;
        public String filename;
        public String language;
        public Instant uploadedAt;
    }






}