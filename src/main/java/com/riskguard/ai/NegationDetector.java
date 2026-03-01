package com.riskguard.ai;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class NegationDetector {
    private static final Pattern NEG_PATTERN = Pattern.compile(
        "\\b(yoktur|olmayacaktır|olmaması|hariçtir|sorumlu değildir|kapsam dışıdır)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public boolean hasNegation(String text) {
        if (text == null) return false;
        return NEG_PATTERN.matcher(text).find();
    }
}
