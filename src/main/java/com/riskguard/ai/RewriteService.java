package com.riskguard.ai;

import org.springframework.stereotype.Service;

@Service
public class RewriteService {
    public String rewriteSafer(String category, String original, String saferFromYaml) {
        if (saferFromYaml != null && !saferFromYaml.isBlank()) return saferFromYaml;
        return "Madde ifadesi karşılıklılık, ölçülülük ve makul üst sınırlar ile yeniden yazılmalıdır.";
    }
}
