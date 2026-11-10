package dev.flashsale.checkout.repository;

import dev.flashsale.checkout.domain.OrderRow;
import dev.flashsale.checkout.domain.OrderStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Second idempotency layer (defense in depth): even if the Redis SETNX lock were
     * ever bypassed or lost, this unique-constraint insert guarantees at most one row
     * per idempotency key. Returns false when a row already exists (ON CONFLICT DO NOTHING).
     */
    public boolean insertIfAbsent(UUID orderId, String idempotencyKey, String sku, String userId, int quantity) {
        int rowsAffected = jdbcTemplate.update(
                """
                INSERT INTO orders (id, idempotency_key, sku, user_id, quantity, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', now())
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                orderId, idempotencyKey, sku, userId, quantity
        );
        return rowsAffected == 1;
    }

    /** Demo convenience for the admin-reset endpoint, not used on any real purchase path. */
    public void deleteAllForSku(String sku) {
        jdbcTemplate.update("DELETE FROM orders WHERE sku = ?", sku);
    }

    public Optional<OrderRow> findById(UUID orderId) {
        try {
            OrderRow row = jdbcTemplate.queryForObject(
                    """
                    SELECT id, idempotency_key, sku, user_id, quantity, status, failure_reason,
                           created_at, processed_at, notified_at
                    FROM orders WHERE id = ?
                    """,
                    (rs, rowNum) -> new OrderRow(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("idempotency_key"),
                            rs.getString("sku"),
                            rs.getString("user_id"),
                            rs.getInt("quantity"),
                            OrderStatus.valueOf(rs.getString("status")),
                            rs.getString("failure_reason"),
                            toInstant(rs.getTimestamp("created_at")),
                            toInstant(rs.getTimestamp("processed_at")),
                            toInstant(rs.getTimestamp("notified_at"))
                    ),
                    orderId
            );
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
