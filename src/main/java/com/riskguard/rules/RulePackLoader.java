package com.riskguard.rules;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

@Component
public class RulePackLoader {
    private final ResourceLoader resourceLoader;
    public RulePackLoader(ResourceLoader rl){ this.resourceLoader = rl; }

    public RulePack load(String path){
        try{
            Resource res = resourceLoader.getResource(path);
            try (InputStream in = res.getInputStream()){
                Yaml yaml = new Yaml();
                return yaml.loadAs(in, RulePack.class);
            }
        }catch(Exception ex){
            throw new RuntimeException("RulePack yüklenemedi: " + path, ex);
        }
    }
}
