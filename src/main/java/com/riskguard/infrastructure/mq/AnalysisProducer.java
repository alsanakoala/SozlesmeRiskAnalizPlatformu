package com.riskguard.infrastructure.mq;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ConditionalOnProperty(prefix = "features.mq", name = "enabled", havingValue = "true")
@Component
public class AnalysisProducer {

    private final RabbitTemplate rabbit;

    public AnalysisProducer(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void requestAnalyze(java.util.UUID documentId) {
        rabbit.convertAndSend("documents", "analyze", documentId.toString());
    }
}
