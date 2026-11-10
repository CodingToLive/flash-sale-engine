package dev.flashsale.orderprocessing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        double failureRate,
        long minProcessingDelayMs,
        long maxProcessingDelayMs
) {
}
