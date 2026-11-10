package dev.flashsale.notification.email;

import dev.flashsale.notification.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Opt-in only (app.email.enabled=true). Point spring.mail.* at any SMTP provider you
 * control via environment variables - deliberately not hardcoded to a specific vendor
 * or to the developer's personal inbox. See README "Notifications" for the rationale.
 */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void send(String toAddress, NotificationPayload payload) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.fromAddress());
        message.setTo(toAddress);
        message.setSubject(payload.subject());
        message.setText(payload.body());
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send opt-in demo email to {}: {}", toAddress, e.getMessage());
        }
    }
}
