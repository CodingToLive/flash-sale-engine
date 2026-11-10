package dev.flashsale.checkout.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Owns the two Redis-backed concurrency-safety primitives used by checkout:
 * - idemp:{key}  -> SETNX lock, so concurrent duplicate requests only let one through.
 * - stock:{sku}  -> atomic check-and-decrement via a Lua script, so concurrent buyers
 *                   can never oversell past zero.
 */
@Component
public class StockAndIdempotencyService {

    private static final long SOLD_OUT = -1L;
    private static final long UNSET = -2L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> decrementScript;

    public StockAndIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.decrementScript = new DefaultRedisScript<>();
        this.decrementScript.setLocation(new ClassPathResource("redis/stock_decrement.lua"));
        this.decrementScript.setResultType(Long.class);
    }

    /** @return the already-stored orderId if a duplicate, or null if this call acquired the lock. */
    public UUID tryAcquireIdempotencyLock(String idempotencyKey, UUID orderId, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempKey(idempotencyKey), orderId.toString(), ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return null;
        }
        String existing = redisTemplate.opsForValue().get(idempKey(idempotencyKey));
        return existing != null ? UUID.fromString(existing) : orderId;
    }

    public void releaseIdempotencyLock(String idempotencyKey) {
        redisTemplate.delete(idempKey(idempotencyKey));
    }

    public enum DecrementResult { OK, SOLD_OUT, STOCK_NOT_SEEDED }

    public DecrementResult decrementStock(String sku, int quantity) {
        Long result = redisTemplate.execute(decrementScript, List.of(stockKey(sku)), String.valueOf(quantity));
        if (result == null || result == UNSET) {
            return DecrementResult.STOCK_NOT_SEEDED;
        }
        if (result == SOLD_OUT) {
            return DecrementResult.SOLD_OUT;
        }
        return DecrementResult.OK;
    }

    public void seedStockIfAbsent(String sku, int initialStock) {
        redisTemplate.opsForValue().setIfAbsent(stockKey(sku), String.valueOf(initialStock));
    }

    /** Unconditionally resets stock to the given value. Used by the demo's admin-reset endpoint. */
    public void resetStock(String sku, int initialStock) {
        redisTemplate.opsForValue().set(stockKey(sku), String.valueOf(initialStock));
    }

    public long remainingStock(String sku) {
        String value = redisTemplate.opsForValue().get(stockKey(sku));
        return value == null ? 0L : Long.parseLong(value);
    }

    private static String stockKey(String sku) {
        return "stock:" + sku;
    }

    private static String idempKey(String idempotencyKey) {
        return "idemp:" + idempotencyKey;
    }
}
