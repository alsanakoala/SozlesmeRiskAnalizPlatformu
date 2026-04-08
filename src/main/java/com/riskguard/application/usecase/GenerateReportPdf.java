package com.riskguard.application.usecase;

import com.riskguard.infrastructure.pdf.PdfRendererClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class GenerateReportPdf {

    private final PdfRendererClient client;

    public GenerateReportPdf(PdfRendererClient client) { this.client = client; }

    public CompletableFuture<byte[]> execute(String themedHtml) {
        return client.renderAsync(themedHtml);
    }
}
