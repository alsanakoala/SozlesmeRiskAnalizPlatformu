package com.riskguard.service;

public class ExtractedTextResult {
    public final String text;
    public final boolean usedOcr;

    public ExtractedTextResult(String text, boolean usedOcr) {
        this.text = text;
        this.usedOcr = usedOcr;
    }
}
