package com.riskguard.dto;

import java.util.UUID;
import java.util.List;

// Bu DTO upload endpoint'inin cevabı olacak:
// - Dokümanın ID'si
// - Toplam risk skoru (0-100 arası gibi)
// - En kritik bulgular (ilk 3, vb.)

public class DocumentUploadResponse {

    private UUID id;
    private double totalScore;
    private List<RiskFindingDTO> topFindings;

    // Constructor (id zorunlu değilse boş constructor da bırakalım)
    public DocumentUploadResponse() {}

    public DocumentUploadResponse(UUID id) {
        this.id = id;
    }

    public DocumentUploadResponse(UUID id, double totalScore, List<RiskFindingDTO> topFindings) {
        this.id = id;
        this.totalScore = totalScore;
        this.topFindings = topFindings;
    }

    // Getters / Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public List<RiskFindingDTO> getTopFindings() {
        return topFindings;
    }

    public void setTopFindings(List<RiskFindingDTO> topFindings) {
        this.topFindings = topFindings;
    }
}
