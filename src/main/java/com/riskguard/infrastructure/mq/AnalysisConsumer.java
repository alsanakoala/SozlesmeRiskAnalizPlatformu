package com.riskguard.infrastructure.mq;

import org.springframework.stereotype.Component;

import com.riskguard.domain.events.AnalysisCompleted;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

@ConditionalOnProperty(prefix = "features.mq", name = "enabled", havingValue = "true")
@Component
public class AnalysisConsumer {

    private final ApplicationEventPublisher events;

    public AnalysisConsumer(ApplicationEventPublisher events) {
        this.events = events;
    }

    @RabbitListener(queues = AnalysisQueues.ANALYZE_REQ)
    public void onAnalyzeMessage(String id) {
        
        long t0 = System.currentTimeMillis();
        UUID documentId = UUID.fromString(id);

        // TODO: OCR -> NLP -> Rules -> Persist
        // Mevcut servis zincirini burada çağır (PdfTextExtractor, ClauseSegmentationService, RuleEngineService, vs.)

        long dur = System.currentTimeMillis() - t0;
        events.publishEvent(new AnalysisCompleted(documentId, "1.0.0", dur));
    }
}
