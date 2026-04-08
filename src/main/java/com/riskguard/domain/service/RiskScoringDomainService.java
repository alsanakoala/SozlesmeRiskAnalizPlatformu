package com.riskguard.domain.service;

import com.riskguard.domain.model.RulePack;

public class RiskScoringDomainService {

    public int calibrateScore(int baseScore, RulePack.RuleDef rule) {
        // Eskisi: baseScore + rule.weight  (DERLEYİCİ HATASI VERİR)
        int weight = rule.getWeight(); // <-- getter kullan
        int calibrated = baseScore + weight;
        // 0-100 aralığına sıkıştır
        return Math.max(0, Math.min(100, calibrated));
    }
}
