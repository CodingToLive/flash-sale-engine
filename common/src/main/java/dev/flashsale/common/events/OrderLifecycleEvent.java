package dev.flashsale.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The single event shape published to the {@code order.events} topic exchange for
 * every hop of an order's life. One shape keeps producers/consumers/tracking-service
 * in sync without a zoo of per-stage DTOs; routingKey/hop tell consumers what changed.
 * {@code notifyEmail} is optional and only ever set when a visitor explicitly opts in
 * to receiving a real email for this order; it is never required for the demo.
 */
public record OrderLifecycleEvent(
        UUID orderId,
        String idempotencyKey,
        OrderHop hop,
        String sku,
        String userId,
        int quantity,
        String notifyEmail,
        String failureReason,
        String notificationSubject,
        String notificationBody,
        Instant timestamp
) {

    public static OrderLifecycleEvent created(UUID orderId, String idempotencyKey, String sku, String userId, int quantity, String notifyEmail, Instant now) {
        return new OrderLifecycleEvent(orderId, idempotencyKey, OrderHop.CREATED, sku, userId, quantity, notifyEmail, null, null, null, now);
    }

    public static OrderLifecycleEvent processing(OrderLifecycleEvent source, Instant now) {
        return withHop(source, OrderHop.PROCESSING, null, null, null, now);
    }

    public static OrderLifecycleEvent confirmed(OrderLifecycleEvent source, Instant now) {
        return withHop(source, OrderHop.CONFIRMED, null, null, null, now);
    }

    public static OrderLifecycleEvent failed(OrderLifecycleEvent source, String reason, Instant now) {
        return withHop(source, OrderHop.FAILED, reason, null, null, now);
    }

    public static OrderLifecycleEvent notified(OrderLifecycleEvent source, String subject, String body, Instant now) {
        return withHop(source, OrderHop.NOTIFIED, null, subject, body, now);
    }

    private static OrderLifecycleEvent withHop(OrderLifecycleEvent source, OrderHop hop, String failureReason, String subject, String body, Instant now) {
        return new OrderLifecycleEvent(
                source.orderId(), source.idempotencyKey(), hop, source.sku(), source.userId(), source.quantity(),
                source.notifyEmail(), failureReason, subject, body, now);
    }
}
