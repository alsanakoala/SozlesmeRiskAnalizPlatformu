package com.riskguard.ai;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ClauseClassifier {

    public enum Category {
        LIABILITY, TERMINATION, CONFIDENTIALITY, INDEMNITY, FORCE_MAJEURE, OTHER
    }

    // Türkçe varyasyonları yakalamak için regex kalıpları
    private static final Pattern P_LIABILITY = Pattern.compile(
        "(sorumluluk|sorumlu\\b|üst\\s*sınır|limit|limiti|tazminat\\s*üst\\s*sınır|" +
        "dolaylı\\s*zarar|sonuçsal\\s*zarar|kar\\s*kaybı|müteselsil\\s*sorumlu)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern P_TERMINATION = Pattern.compile(
        "(fesih|tek\\s*taraflı\\s*fesih|ihbar\\s*süresi|haklı\\s*neden|derhal\\s*fesih)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern P_CONFIDENTIALITY = Pattern.compile(
        "(gizlilik|ticari\\s*sır|kvkk|kişisel\\s*veri|gizli\\s*bilgi|ifşa)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern P_INDEMNITY = Pattern.compile(
        "(tazmin|indemnity|tazminat\\s*yükümlülüğü|tazmin\\s*etmek)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern P_FORCE_MAJEURE = Pattern.compile(
        "(mücbir\\s*sebep|force\\s*majeure|ifa\\s*imkansızlığı|ifa\\s*imkânsızlığı|öngörülemez)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public Category classify(String text) {
        if (text == null) return Category.OTHER;

        // Türkçe’ye duyarlı lower (ama regex zaten CASE_INSENSITIVE)
        String t = text.toLowerCase(Locale.ROOT);

        if (P_LIABILITY.matcher(t).find())     return Category.LIABILITY;
        if (P_TERMINATION.matcher(t).find())   return Category.TERMINATION;
        if (P_CONFIDENTIALITY.matcher(t).find()) return Category.CONFIDENTIALITY;
        if (P_INDEMNITY.matcher(t).find())     return Category.INDEMNITY;
        if (P_FORCE_MAJEURE.matcher(t).find()) return Category.FORCE_MAJEURE;

        return Category.OTHER;
    }
}
