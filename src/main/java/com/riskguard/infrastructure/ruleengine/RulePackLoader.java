package com.riskguard.infrastructure.ruleengine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.riskguard.domain.model.RulePack;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component("infraRulePackLoader")
public class RulePackLoader {

    private final AtomicReference<RulePack> current = new AtomicReference<>();

    public RulePackLoader() {
        // Uygulama açılışında default YAML'ı classpath'ten yükle
        try (InputStream is = getClass().getResourceAsStream("/risk/rulepack.yml")) {
            this.current.set(readYaml(is));
        } catch (Exception e) {
            throw new IllegalStateException("Default rulepack yüklenemedi", e);
        }
    }

    public RulePack getCurrent() { return current.get(); }

    public void reloadFromYaml(byte[] yamlBytes) {
        RulePack rp = readYaml(yamlBytes);
        rp.setCreatedAt(Instant.now());
        current.set(rp);
    }

    private RulePack readYaml(InputStream is) {
    try {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // <-- EKLE
        RulePack rp = mapper.readValue(is, RulePack.class);
        if (rp.getVersion() == null) rp.setVersion("1.0.0");
        if (rp.getCreatedAt() == null) rp.setCreatedAt(java.time.Instant.now());
        return rp;
    } catch (Exception e) {
        throw new IllegalArgumentException("Rulepack YAML parse hatası", e);
    }
    }

    private RulePack readYaml(byte[] bytes) {
        try (InputStream is = new java.io.ByteArrayInputStream(bytes)) {
            return readYaml(is);
        } catch (Exception e) {
            throw new IllegalArgumentException("Rulepack YAML okuma hatası", e);
        }
    }
}
