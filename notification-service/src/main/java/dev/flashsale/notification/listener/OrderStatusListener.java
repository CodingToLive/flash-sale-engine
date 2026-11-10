package dev.flashsale.notification.listener;

import dev.flashsale.common.events.OrderHop;
import dev.flashsale.common.events.OrderLifecycleEvent;
import dev.flashsale.common.rabbit.RabbitTopology;
import dev.flashsale.notification.config.AppProperties;
import dev.flashsale.notification.email.EmailRateLimiter;
import dev.flashsale.notification.email.EmailSender;
import dev.flashsale.notification.email.NotificationPayload;
import dev.flashsale.notification.email.NotificationPayloadBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderStatusListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusListener.class);

    private final AppProperties appProperties;
    private final NotificationPayloadBuilder payloadBuilder;
    private final EmailSender emailSender;
    private final EmailRateLimiter rateLimiter;
    private final OrderEventPublisher eventPublisher;

    public OrderStatusListener(AppProperties appProperties,
                                NotificationPayloadBuilder payloadBuilder,
                                EmailSender emailSender,
                                EmailRateLimiter rateLimiter,
                                OrderEventPublisher eventPublisher) {
        this.appProperties = appProperties;
        this.payloadBuilder = payloadBuilder;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE_NOTIFICATION)
    public void onOrderStatusChanged(OrderLifecycleEvent event) {
        if (event.hop() != OrderHop.CONFIRMED && event.hop() != OrderHop.FAILED) {
            log.warn("Ignoring unexpected hop {} on notification queue for order {}", event.hop(), event.orderId());
            return;
        }

        NotificationPayload payload = payloadBuilder.build(event);
        log.info("Notification for order {}: {}", event.orderId(), payload.subject());

        maybeSendRealEmail(event, payload);

        eventPublisher.publish(OrderLifecycleEvent.notified(event, payload.subject(), payload.body(), Instant.now()));
    }

    private void maybeSendRealEmail(OrderLifecycleEvent event, NotificationPayload payload) {
        boolean visitorOptedIn = event.notifyEmail() != null && !event.notifyEmail().isBlank();
        if (!appProperties.enabled() || !visitorOptedIn) {
            return;
        }
        if (!rateLimiter.tryAcquire()) {
            log.info("Email rate limit hit - skipping real send for order {}", event.orderId());
            return;
        }
        emailSender.send(event.notifyEmail(), payload);
    }
}
