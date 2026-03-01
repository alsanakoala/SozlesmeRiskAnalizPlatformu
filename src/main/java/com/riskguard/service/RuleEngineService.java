package com.riskguard.service;

import com.riskguard.rules.*;
import com.riskguard.entity.RiskFindingEntity;
import com.riskguard.model.RiskCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
public class RuleEngineService {
    private final RulePack rules;

    // 🔥 EKLEDİĞİMİZ KISIM:
    // Her kategori için ağırlık. 1.0 = çok kritik, 0.4 = nispeten hafif.
    // Burada ana fikir: bazı riskler hukuki olarak öldürücü (liability / personal data),
    // bazıları ise daha "ticari pazarlık" seviyesinde.
    private static final Map<RiskCategory, Double> CATEGORY_WEIGHTS = Map.of(
        RiskCategory.LIMITATION_OF_LIABILITY, 1.0,
        RiskCategory.INDEMNITY,               0.9,
        RiskCategory.CONFIDENTIALITY,         0.95, // KVKK / kişisel veri gibi şeyler buraya giriyor
        RiskCategory.GOVERNING_LAW,           0.6,
        RiskCategory.AUTO_RENEWAL,            0.4,
        RiskCategory.OTHER,                   0.5
    );

    public RuleEngineService(RulePackLoader loader, @Value("${app.rules.path}") String rulesPath){
        this.rules = loader.load(rulesPath);
    }

    
    
    public List<RiskFindingEntity> evaluate(UUID documentId,
                                        List<UUID> clauseIds,
                                        List<String> clauseTexts) {

    List<RiskFindingEntity> results = new ArrayList<>();

    // varsayım: this.rules -> List<RuleDef>
    for (int clauseIndex = 0; clauseIndex < clauseTexts.size(); clauseIndex++) {
        String clauseText = clauseTexts.get(clauseIndex);
        UUID clauseId = clauseIds.get(clauseIndex);

        for (RuleDef rule : rules.rules) {

            // 1. Bu kurala ait denenecek pattern listesini hazırla
            List<String> candidatePatterns = new ArrayList<>();

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

            // Hiç pattern yoksa devam et
            if (candidatePatterns.isEmpty()) {
                continue;
            }

            boolean matched = false;
            String matchedSnippet = null;

            // 2. Her bir regex'i bu clause üzerinde dene
            for (String p : candidatePatterns) {
                if (p == null || p.isEmpty()) continue;

                Pattern compiled = Pattern.compile("(?is)" + p);
                Matcher m = compiled.matcher(clauseText);

                if (m.find()) {
                    matched = true;

                    // snippet = eşleşen kısım
                    matchedSnippet = clauseText.substring(
                        Math.max(m.start(), 0),
                        Math.min(m.end(), clauseText.length())
                    );

                    break; // bu kural tetiklendi, diğer pattern'lere bakmaya gerek yok
                }
            }

            if (!matched) {
                continue;
            }

            // 3. Eşleştiyse RiskFindingEntity oluştur
            RiskFindingEntity finding = new RiskFindingEntity();
            finding.setId(UUID.randomUUID());
            finding.setDocumentId(documentId);
            finding.setClauseId(clauseId);

            // Kategori
            // Senin RuleDef.category şu an RiskCategory enum (sen öyle tanımladın).
            // O yüzden direkt kullanıyoruz:
            finding.setCategory(rule.getCategory());

            // Rule ID
            finding.setRuleId(rule.getId());

            // Skor mantığı:
            Double ruleScore = rule.getScore();
            if (ruleScore == null) {
                // geri uyumluluk: eski weight/severity'den hesapla
                // Bu formül senin sisteminde farklıysa burayı değiştir.
                ruleScore = rule.getWeight() * rule.getSeverity();
            }
            finding.setScore(ruleScore);

            // Şimdilik sabit güven değeri
            finding.setConfidence(1.0);

            // Açıklama / Mitigation
            finding.setExplanation(rule.getExplanation());
            finding.setMitigation(rule.getMitigation());

            // Snippet (eşleşen parça)
            if (matchedSnippet != null) {
                finding.setSnippet(matchedSnippet);
            } else {
                finding.setSnippet(clauseText);
            }

            results.add(finding);
        }
    }

    return results;
}


    public double aggregateScore(List<RiskFindingEntity> findings){
        // remaining mantığını koruyoruz, ama her bulguyu kategori ağırlığı ile çarpıyoruz

        double remaining = 1.0;

        for (RiskFindingEntity f : findings){
            // bulgunun kendi şiddetini normalize et (0.0 - 1.0)
            double base = Math.max(0.0, Math.min(1.0, f.getScore()/100.0));

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

            // önceki formülünde s*0.6 vardı.
            // Artık 'effective'ı kullanıyoruz ki kritik kategoriler sistemi daha çok etkilesin.
            remaining *= (1.0 - effective * 0.6);
        }

        double finalScore = (1.0 - remaining) * 100.0;

        // tek ondalık
        return Math.round(finalScore * 10.0) / 10.0;
    }
}
