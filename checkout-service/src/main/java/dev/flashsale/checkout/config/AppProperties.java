package dev.flashsale.checkout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String sku, String adminSecret, long idempotencyTtlSeconds) {
}
