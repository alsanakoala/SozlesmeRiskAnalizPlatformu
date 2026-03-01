package com.riskguard.dto;

import java.util.List;

public class AnalyzeResponse {

    // Toplam risk skoru
    // (AnalysisService şu an buna resp.score diyor)
    public double score;

    // Tespit edilen bulguların listesi
    public List<RiskFindingDTO> findings;
    // 🔽 Eklediklerimiz
    public Integer clauseCount;
    public Integer findingCount;
    public Integer textLength;

    // ---- Getter / Setter'lar ----

    // upload() tarafında analysisResult.getTotalScore() çağırıyoruz.
    // O yüzden getTotalScore() aslında score'u dönecek.
    public double getTotalScore() {
        return score;
    }

    public void setTotalScore(double totalScore) {
        this.score = totalScore;
    }

    // upload() tarafında analysisResult.getFindings() çağırıyoruz.
    public List<RiskFindingDTO> getFindings() {
        return findings;
    }

    public void setFindings(List<RiskFindingDTO> findings) {
        this.findings = findings;
    }
}
