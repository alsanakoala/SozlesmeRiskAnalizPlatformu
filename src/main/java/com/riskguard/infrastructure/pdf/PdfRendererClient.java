package com.riskguard.infrastructure.pdf;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component("infraPdfRendererClient")
public class PdfRendererClient {

    private final RestTemplate rt = new RestTemplate();

    @Value("${pdf.renderer.url:http://localhost:3005/render}")
    private String renderUrl;

    @Value("${pdf.renderer.token:}")
    private String token;

    @Value("${pdf.renderer.base-url:http://localhost:8080/}")
    private String baseUrl;

    @TimeLimiter(name = "pdfRender")
    @Retry(name = "pdfRender")
    @CircuitBreaker(name = "pdfRender", fallbackMethod = "fallback")
    public CompletableFuture<byte[]> renderAsync(String html) {
        return CompletableFuture.supplyAsync(() -> render(html));
    }

    private byte[] render(String html) {
        Assert.hasText(html, "HTML boş olamaz");
        Assert.hasText(baseUrl, "baseUrl boş olamaz");

        var payload = Map.of(
                "html", html,
                "baseUrl", baseUrl,
                "waitUntil", "networkidle0",
                "pdf", Map.of("printBackground", true, "scale", 1.0)
        );

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (!token.isBlank()) h.setBearerAuth(token);

        ResponseEntity<byte[]> resp = rt.exchange(
                renderUrl, HttpMethod.POST, new HttpEntity<>(payload, h), byte[].class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("Renderer başarısız: " + resp.getStatusCode());
        }
        return resp.getBody();
    }

    // Fallback: boş PDF yerine anlamlı hata imzası
    private CompletableFuture<byte[]> fallback(String html, Throwable ex) {
        String msg = "PDF render başarısız: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
        return CompletableFuture.completedFuture(("[RENDER_ERROR]\n" + msg).getBytes());
    }
}
