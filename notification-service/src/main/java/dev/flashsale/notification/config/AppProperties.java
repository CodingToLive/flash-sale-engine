package dev.flashsale.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Real email delivery is opt-in and off by default (see README "Notifications").
 * When disabled, the service still does real work: it renders the notification
 * payload and publishes it for tracking-service to show as an in-page preview.
 */
@ConfigurationProperties(prefix = "app.email")
public record AppProperties(
        boolean enabled,
        String fromAddress,
        int rateLimitSeconds
) {
}
