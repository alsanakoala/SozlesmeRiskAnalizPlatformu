package com.riskguard.interfaces.rest;

import com.riskguard.application.usecase.GenerateReportPdf;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final GenerateReportPdf generateReportPdf;

    public ReportController(GenerateReportPdf generateReportPdf) {
        this.generateReportPdf = generateReportPdf;
    }

    @PostMapping(value="/pdf", consumes = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> renderPdf(@RequestBody String html) throws ExecutionException, InterruptedException {
        byte[] pdf = generateReportPdf.execute(html).get(); // async -> sync köprü
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.pdf")
                .body(pdf);
    }
}
