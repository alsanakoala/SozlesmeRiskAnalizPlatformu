package com.riskguard.dto;

import java.util.UUID;
import com.riskguard.model.RiskCategory;

public class RiskFindingDTO {
    public UUID id;
    public UUID clauseId;
    public RiskCategory category;
    public String ruleId;
    public double score;
    public double confidence;
    public String snippet;
    public String explanation;
    public String mitigation;

    public double getScore() 
    {
    return score;
    }
    public void setScore(double score) 
    {
    this.score = score;
    }
}
