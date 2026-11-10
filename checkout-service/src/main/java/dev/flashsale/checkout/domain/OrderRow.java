package dev.flashsale.checkout.domain;

import java.time.Instant;
import java.util.UUID;

public record OrderRow(
        UUID id,
        String idempotencyKey,
        String sku,
        String userId,
        int quantity,
        OrderStatus status,
        String failureReason,
        Instant createdAt,
        Instant processedAt,
        Instant notifiedAt
) {
}
