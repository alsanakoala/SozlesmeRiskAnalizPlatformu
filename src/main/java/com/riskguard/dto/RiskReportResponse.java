package com.riskguard.dto;

import java.util.List;

/**
 * Sözleşme analiz raporunun DTO (Data Transfer Object) yapısı.
 * - documentId, filename, language, totalScore -> genel bilgiler
 * - findings -> her riskli maddeye dair özetler
 */
public class RiskReportResponse {
    public String documentId;
    public String filename;
    public String language;
    public double totalScore;
    public List<FindingSummary> findings;
    // Debug / diagnostic bilgiler (UI içi gösterim için)
    public Integer clauseCount;   // bu dokümandaki toplam clause sayısı
    public Integer findingCount;  // bu doküman için üretilen toplam risk bulgusu
    public Integer textLength;    // belge metninin karakter uzunluğu
    // Metin OCR'dan mı geldi (true) yoksa doğrudan PDF parse mı (false)?
    // DocumentEntity.ocrUsed'den dolduruyoruz.
    public Boolean ocrUsed;
    // Kategori bazlı ortalama skorlar (örnek: "TERMINATION" -> 85.0)
    public java.util.Map<String, Double> avgScorePerCategory;


    /**
     * Her riskli bulguya ait özet bilgileri içerir.
     * Rapor ekranı ve JSON API çıktısı bu sınıf üzerinden üretilir.
     */
    public static class FindingSummary {
        public String category;      // ENUM: INDEMNITY, TERMINATION, ...
        public String ruleId;        // YAML'daki kural kimliği
        public double score;         // Risk puanı
        public double confidence;    // Eşleşme güven skoru
        public String snippet;       // Metin parçası
        public String explanation;   // İngilizce açıklama (kural tabanlı)
        public String mitigation;    // Opsiyonel: risk azaltıcı öneri (DB'de varsa)

        // 🇹🇷 Yeni eklenen alanlar:
        public String humanTitle;    // Türkçe kategori başlığı (ör: "Tek Taraflı Fesih Hakkı")
        public String advice;        // Türkçe tavsiye / yorum (ör: "Bu madde pazarlık edilmeli.")
    }
}
