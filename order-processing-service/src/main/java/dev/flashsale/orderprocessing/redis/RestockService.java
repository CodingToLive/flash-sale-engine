package dev.flashsale.orderprocessing.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Compensating action for a failed order: give the reserved stock back.
 * Simple saga pattern - checkout optimistically decremented, processing undoes it on failure.
 */
@Component
public class RestockService {

    private final StringRedisTemplate redisTemplate;

    public RestockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void restock(String sku, int quantity) {
        redisTemplate.opsForValue().increment("stock:" + sku, quantity);
    }
}
