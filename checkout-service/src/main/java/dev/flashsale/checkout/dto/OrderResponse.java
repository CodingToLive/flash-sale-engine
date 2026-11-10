package dev.flashsale.checkout.dto;

import dev.flashsale.checkout.domain.OrderRow;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String status,
        String sku,
        int quantity,
        Instant createdAt,
        Instant processedAt,
        Instant notifiedAt,
        String failureReason
) {
    public static OrderResponse from(OrderRow row) {
        return new OrderResponse(
                row.id(),
                row.status().name(),
                row.sku(),
                row.quantity(),
                row.createdAt(),
                row.processedAt(),
                row.notifiedAt(),
                row.failureReason()
        );
    }
}
