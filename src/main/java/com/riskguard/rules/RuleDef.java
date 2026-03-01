package com.riskguard.rules;

import com.riskguard.model.RiskCategory;
import java.util.List;

public class RuleDef {

    // --- mevcut alanlar ---
    public String id;
    public RiskCategory category;
    public String name;
    private String pattern;
    public double weight;
    public double severity;
    public String locale;
    public String mitigation;

    // --- yeni alanlar ---
    private Double score;
    private String explanation;
    private List<String> patterns;

    // --- getter & setter ---

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public RiskCategory getCategory() {
        return category;
    }
    public void setCategory(RiskCategory category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public double getWeight() {
        return weight;
    }
    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getSeverity() {
        return severity;
    }
    public void setSeverity(double severity) {
        this.severity = severity;
    }

    public String getLocale() {
        return locale;
    }
    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getMitigation() {
        return mitigation;
    }
    public void setMitigation(String mitigation) {
        this.mitigation = mitigation;
    }

    public Double getScore() {
        return score;
    }
    public void setScore(Double score) {
        this.score = score;
    }

    public String getExplanation() {
        return explanation;
    }
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getPatterns() {
        return patterns;
    }
    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }
}
