package dev.flashsale.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default, safe-by-default sender: logs what would have been sent and stops there.
 * Active unless app.email.enabled=true is explicitly set (requires real SMTP config).
 */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
public class NoopEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopEmailSender.class);

    @Override
    public void send(String toAddress, NotificationPayload payload) {
        log.info("[email disabled] would have sent to {}: {}", toAddress, payload.subject());
    }
}
