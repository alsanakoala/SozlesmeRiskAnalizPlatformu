package com.riskguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class PdfRendererClient {

    private final HttpClient http;
    private final String endpoint;
    private final String token;

    public PdfRendererClient(
            @Value("${riskguard.renderer.url:http://localhost:3005/render}") String endpoint,
            @Value("${riskguard.renderer.token:}") String token
    ) {
        this.http = HttpClient.newHttpClient();
        this.endpoint = endpoint;
        this.token = token;
    }

    public byte[] renderHtmlToPdf(String html, String baseUrl) {
        try {
            // Şık kurumsal üst bilgi (Header) ve alt bilgi (Footer) şablonları
            String headerTemplate = "<div style='font-size: 9px; color: #888; width: 100%; text-align: right; padding-right: 15mm; font-family: Arial, sans-serif; text-transform: uppercase; letter-spacing: 1px;'>Sözleşme Risk Analiz Raporu | GİZLİ</div>";
            String footerTemplate = "<div style='font-size: 10px; color: #555; width: 100%; text-align: center; border-top: 1px solid #eee; padding-top: 5px; margin: 0 15mm; font-family: Arial, sans-serif;'>Sayfa <span class=\"pageNumber\"></span> / <span class=\"totalPages\"></span></div>";

            String json = "{"
                    + "\"html\":" + toJsonString(html) + ","
                    + "\"baseUrl\":" + toJsonString(baseUrl) + ","
                    + "\"pdf\":{"
                    +   "\"format\":\"A4\","
                    +   "\"margin\":{\"top\":\"20mm\",\"right\":\"12mm\",\"bottom\":\"20mm\",\"left\":\"12mm\"},"
                    +   "\"printBackground\":true,"
                    +   "\"preferCSSPageSize\":true,"
                    +   "\"displayHeaderFooter\":true,"
                    +   "\"headerTemplate\":" + toJsonString(headerTemplate) + ","
                    +   "\"footerTemplate\":" + toJsonString(footerTemplate)
                    + "},"
                    + "\"waitUntil\":\"networkidle0\","
                    + "\"timeoutMs\":30000"
                    + "}";

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json");

            if (token != null && !token.isBlank()) {
                rb.header("Authorization", "Bearer " + token);
            }

            HttpRequest req = rb.POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                return resp.body();
            } else {
                String detail = new String(resp.body() == null ? new byte[0] : resp.body());
                throw new RuntimeException("Renderer HTTP " + code + " - " + detail);
            }
        } catch (Exception e) {
            throw new RuntimeException("Render servisine bağlanırken hata: " + e.getMessage(), e);
        }
    }

    private static String toJsonString(String s) {
        if (s == null) return "null";
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
}