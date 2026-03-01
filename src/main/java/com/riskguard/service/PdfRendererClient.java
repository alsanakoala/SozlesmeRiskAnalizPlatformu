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
            @Value("${renderer.url:http://localhost:3005/render}") String endpoint,
            @Value("${renderer.token:}") String token
    ) {
        this.http = HttpClient.newHttpClient();
        this.endpoint = endpoint;
        this.token = token;
    }

    public byte[] renderHtmlToPdf(String html, String baseUrl) {
        try {
            // server.js içindeki alanlarla birebir uyuşan JSON gövde
            String json = "{"
                    + "\"html\":" + toJsonString(html) + ","
                    + "\"baseUrl\":" + toJsonString(baseUrl) + ","
                    + "\"pdf\":{"
                    +   "\"format\":\"A4\","
                    +   "\"margin\":{\"top\":\"12mm\",\"right\":\"12mm\",\"bottom\":\"14mm\",\"left\":\"12mm\"},"
                    +   "\"printBackground\":true,"
                    +   "\"preferCSSPageSize\":true,"
                    +   "\"displayHeaderFooter\":true"
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

            // DOĞRUDAN PDF baytlarını alıyoruz (asla JSON'a sarmalamıyoruz)
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                // İçerik tipi application/pdf olmalı — ama olmasa da baytlar önemli
                return resp.body();
            } else {
                String detail = new String(resp.body() == null ? new byte[0] : resp.body());
                throw new RuntimeException("Renderer HTTP " + code + " - " + detail);
            }
        } catch (Exception e) {
            throw new RuntimeException("Render servisine bağlanırken hata: " + e.getMessage(), e);
        }
    }

    // Basit JSON kaçışlayıcı
    private static String toJsonString(String s) {
        if (s == null) return "null";
        String escaped = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
}
