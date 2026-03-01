package com.riskguard.domain.events;

import java.util.UUID;

public record AnalysisCompleted(UUID documentId, String rulepackVersion, long durationMs) {}
