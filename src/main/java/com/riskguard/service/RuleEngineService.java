package com.riskguard.service;

import com.riskguard.rules.*;
import com.riskguard.entity.RiskFindingEntity;
import com.riskguard.model.RiskCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class RuleEngineService {
    private final RulePack rules;

    private static final Map<RiskCategory, Double> CATEGORY_WEIGHTS = Map.ofEntries(
            Map.entry(RiskCategory.LIMITATION_OF_LIABILITY, 1.00),
            Map.entry(RiskCategory.INDEMNITY, 0.95),
            Map.entry(RiskCategory.TERMINATION, 0.95),
            Map.entry(RiskCategory.PAYMENT_TERMS, 0.90),
            Map.entry(RiskCategory.INTELLECTUAL_PROPERTY_TRANSFER, 0.90),
            Map.entry(RiskCategory.DATA_PROTECTION, 0.90),
            Map.entry(RiskCategory.CONFIDENTIALITY, 0.85),
            Map.entry(RiskCategory.INSURANCE_OBLIGATION, 0.80),
            Map.entry(RiskCategory.JURISDICTION, 0.75),
            Map.entry(RiskCategory.GOVERNING_LAW, 0.70),
            Map.entry(RiskCategory.CLAIMS_AND_DISPUTES, 0.75),
            Map.entry(RiskCategory.AUTO_RENEWAL, 0.55),
            Map.entry(RiskCategory.SCOPE_OF_WORK, 0.80),
            Map.entry(RiskCategory.WARRANTIES, 0.85),
            Map.entry(RiskCategory.OTHER, 0.60));

    public RuleEngineService(RulePackLoader loader, @Value("${app.rules.path}") String rulesPath) {
        this.rules = loader.load(rulesPath);
    }

    public List<RiskFindingEntity> evaluate(UUID documentId,
            List<UUID> clauseIds,
            List<String> clauseTexts) {

        List<RiskFindingEntity> results = new ArrayList<>();
        Set<String> uniqueCheck = new HashSet<>();

        for (int clauseIndex = 0; clauseIndex < clauseTexts.size(); clauseIndex++) {
            String clauseText = clauseTexts.get(clauseIndex);
            UUID clauseId = clauseIds.get(clauseIndex);

            if (clauseText == null || clauseText.trim().isEmpty())
                continue;

            for (RuleDef rule : rules.rules) {

                String comboKey = (clauseId != null ? clauseId.toString() : "GLOBAL") + "-" + rule.getId();
                if (uniqueCheck.contains(comboKey))
                    continue;

                // 1. Bu kurala ait denenecek pattern listesini hazırla
                List<String> candidatePatterns = new ArrayList<>();
                if (rule.getPatterns() != null)
                    candidatePatterns.addAll(rule.getPatterns());
                if (rule.getPattern() != null)
                    candidatePatterns.add(rule.getPattern());

                // yeni format (List<String> patterns)
                List<String> fromYamlList = rule.getPatterns();
                if (fromYamlList != null && !fromYamlList.isEmpty()) {
                    candidatePatterns.addAll(fromYamlList);
                }

                // eski format (tekil pattern String)
                String singlePattern = rule.getPattern();
                if (singlePattern != null && !singlePattern.isEmpty()) {
                    candidatePatterns.add(singlePattern);
                }

                if (candidatePatterns.isEmpty())
                    continue;

                boolean matched = false;
                String matchedSnippet = null;

                // 2. Her bir regex'i bu clause üzerinde dene
                for (String p : candidatePatterns) {
                    if (p == null || p.isEmpty())
                        continue;

                    try {
                        // Regex'i derle
                        Pattern compiled = Pattern.compile("(?is)" + p);
                        Matcher m = compiled.matcher(clauseText);

                        if (m.find()) {
                            matched = true;

                            // snippet = eşleşen kısım
                            matchedSnippet = m.group().trim();
                            if (matchedSnippet.length() > 250) {
                                matchedSnippet = matchedSnippet.substring(0, 247) + "...";
                            }
                            break;
                        }
                    } catch (PatternSyntaxException e) {
                        // Hatalı regex patternlerini sistemin çökmemesi için atlıyoruz
                        continue;
                    }
                } // patterns loop end

                if (matched) {
                    // 3. Eşleştiyse RiskFindingEntity oluştur
                    RiskFindingEntity finding = new RiskFindingEntity();
                    finding.setId(UUID.randomUUID());
                    finding.setDocumentId(documentId);
                    finding.setClauseId(clauseId);
                    finding.setCategory(rule.getCategory());
                    finding.setRuleId(rule.getId());

                    Double ruleScore = rule.getScore();
                    if (ruleScore == null) {
                        // 🔽 HATA BURADAYDI: Double (büyük D) kullanarak null kontrolüne izin veriyoruz
                        Double weight = (Double.valueOf(rule.getWeight()) != null ? rule.getWeight() : 1.0);
                        Double severity = (Double.valueOf(rule.getSeverity()) != null ? rule.getSeverity() : 1.0);
                        ruleScore = weight * severity;
                    }
                    finding.setScore(ruleScore);
                    finding.setConfidence(1.0);
                    finding.setExplanation(rule.getExplanation());
                    finding.setMitigation(rule.getMitigation());

                    if (matchedSnippet != null) {
                        finding.setSnippet(matchedSnippet);
                    } else {
                        finding.setSnippet(clauseText);
                    }

                    results.add(finding);
                    uniqueCheck.add(comboKey);
                }
            } // rules loop end
        } // clauses loop end
        return results;
    }

    public double aggregateScore(List<RiskFindingEntity> findings) {
        // remaining mantığını koruyoruz, ama her bulguyu kategori ağırlığı ile
        // çarpıyoruz

        double remaining = 1.0;
        Set<String> processedRules = new HashSet<>();
        for (RiskFindingEntity f : findings) {
            if (processedRules.contains(f.getRuleId()))
                continue;

            double base = Math.max(0.0, Math.min(1.0, f.getScore() / 100.0));

            // kategori ağırlığını al
            double weight = 0.7; // fallback default
            if (f.getCategory() != null) {
                Double w = CATEGORY_WEIGHTS.get(f.getCategory());
                if (w != null) {
                    weight = w;
                }
            }

            // efektif şiddet
            double effective = base * weight;

            remaining *= (1.0 - effective * 0.85);
            processedRules.add(f.getRuleId());
        }

        double finalScore = (1.0 - remaining) * 100.0;
        finalScore = Math.max(0.0, Math.min(100.0, finalScore));

        // tek ondalık
        return Math.round(finalScore * 10.0) / 10.0;
    }
}