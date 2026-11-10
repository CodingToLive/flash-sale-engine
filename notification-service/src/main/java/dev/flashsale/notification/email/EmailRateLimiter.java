package dev.flashsale.notification.email;

import dev.flashsale.notification.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Global throttle (not per-user) deliberately simple for a demo: at most one real
 * email every rateLimitSeconds, so a traffic-spike/duplicate simulation with hundreds
 * of fake orders can never trigger a burst of real SMTP sends.
 */
@Component
public class EmailRateLimiter {

    private final AppProperties appProperties;
    private final AtomicLong lastSentAtEpochMs = new AtomicLong(0);

    public EmailRateLimiter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long windowMs = appProperties.rateLimitSeconds() * 1000L;
        long previous = lastSentAtEpochMs.get();
        if (now - previous < windowMs) {
            return false;
        }
        return lastSentAtEpochMs.compareAndSet(previous, now);
    }
}
