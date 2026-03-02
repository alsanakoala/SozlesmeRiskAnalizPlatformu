package com.riskguard.entity;

import jakarta.persistence.*;
import java.util.UUID;
import com.riskguard.model.RiskCategory;

@Entity
@Table(name = "risk_finding")
public class RiskFindingEntity {

    @Id
    private UUID id;

    @Column(name="document_id")
    private UUID documentId;

    @Column(name="clause_id")
    private UUID clauseId;

    @Enumerated(EnumType.STRING)
    private RiskCategory category;

    private String ruleId;

    private double score;
    private double confidence;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String explanation;

    // 🔥 YENİ ALAN:
    @Lob
    @Column(columnDefinition = "TEXT")
    private String mitigation;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    // getters/setters...
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getClauseId() { return clauseId; }
    public void setClauseId(UUID clauseId) { this.clauseId = clauseId; }

    public RiskCategory getCategory() { return category; }
    public void setCategory(RiskCategory category) { this.category = category; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    
    public String getMitigation() { return mitigation; }
    public void setMitigation(String mitigation) { this.mitigation = mitigation; }
}
