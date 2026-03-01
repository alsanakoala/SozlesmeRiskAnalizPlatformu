package com.riskguard.domain.model;

import java.time.Instant;
import java.util.List;

public class RulePack {

    private String id;              // örn: "default"
    private String version;         // örn: "1.0.0"
    private Instant createdAt;      // YAML'da yoksa loader set edecek
    private List<RuleDef> rules;    // kurallar listesi

    // --- getters / setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<RuleDef> getRules() { return rules; }
    public void setRules(List<RuleDef> rules) { this.rules = rules; }

    // --- iç sınıf: tek tek kural tanımı ---
    public static class RuleDef {
        private String id;          // <-- YENİ ALAN (YAML'deki id’yi karşılar)
        private String tag;
        private String language;
        private String pattern;
        private int weight;
        private String messageTpl;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }

        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }

        public String getMessageTpl() { return messageTpl; }
        public void setMessageTpl(String messageTpl) { this.messageTpl = messageTpl; }
    }
}
