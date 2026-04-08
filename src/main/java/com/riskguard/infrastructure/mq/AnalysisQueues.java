package com.riskguard.infrastructure.mq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.core.*;

@ConditionalOnProperty(prefix = "features.mq", name = "enabled", havingValue = "true")
@Configuration
public class AnalysisQueues {
    public static final String ANALYZE_REQ = "documents.analyze.request";

    @Bean Queue analyzeQueue() { return new Queue(ANALYZE_REQ, true); }

    @Bean Exchange analyzeExchange() {
        return ExchangeBuilder.directExchange("documents").durable(true).build();
    }

    @Bean Binding analyzeBinding() {
        return BindingBuilder.bind(analyzeQueue()).to(analyzeExchange()).with("analyze").noargs();
    }
}
