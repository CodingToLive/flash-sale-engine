package dev.flashsale.orderprocessing.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * The conditional {@code WHERE status = 'PENDING'} is what makes this an idempotent
 * consumer: if RabbitMQ redelivers the same order.created message (at-least-once
 * delivery), the second UPDATE affects zero rows instead of double-processing.
 * This is a distinct guarantee from checkout-service's producer-side idempotency.
 */
@Repository
public class OrderStatusRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean markConfirmed(UUID orderId) {
        int rows = jdbcTemplate.update(
                "UPDATE orders SET status = 'CONFIRMED', processed_at = now() WHERE id = ? AND status = 'PENDING'",
                orderId);
        return rows == 1;
    }

    public boolean markFailed(UUID orderId, String reason) {
        int rows = jdbcTemplate.update(
                "UPDATE orders SET status = 'FAILED', failure_reason = ?, processed_at = now() WHERE id = ? AND status = 'PENDING'",
                reason, orderId);
        return rows == 1;
    }
}
