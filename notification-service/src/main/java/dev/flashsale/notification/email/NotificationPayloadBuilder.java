package dev.flashsale.notification.email;

import dev.flashsale.common.events.OrderHop;
import dev.flashsale.common.events.OrderLifecycleEvent;
import org.springframework.stereotype.Component;

@Component
public class NotificationPayloadBuilder {

    public NotificationPayload build(OrderLifecycleEvent event) {
        if (event.hop() == OrderHop.CONFIRMED) {
            return new NotificationPayload(
                    "Your flash sale order is confirmed!",
                    "Good news - order %s for %d x %s is confirmed. Thanks for shopping the flash sale!"
                            .formatted(shortId(event), event.quantity(), event.sku())
            );
        }
        return new NotificationPayload(
                "We couldn't complete your order",
                "Sorry - order %s for %d x %s could not be completed (%s). Any reserved stock has been released."
                        .formatted(shortId(event), event.quantity(), event.sku(), safe(event.failureReason()))
        );
    }

    private static String shortId(OrderLifecycleEvent event) {
        return event.orderId().toString().substring(0, 8);
    }

    private static String safe(String value) {
        return value == null ? "unknown reason" : value;
    }
}
