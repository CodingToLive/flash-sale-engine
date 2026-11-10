package dev.flashsale.checkout.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the test that actually backs the "no overselling, exactly-once order"
 * claims made in the README and demonstrated live by the frontend's spike/duplicate
 * buttons - not just manual observation, but hundreds of real concurrent threads
 * against a real Redis instance.
 */
@Testcontainers
class StockAndIdempotencyServiceConcurrencyTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StockAndIdempotencyService stockService;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        stockService = new StockAndIdempotencyService(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void neverOversellsUnderHeavyConcurrency() throws InterruptedException {
        String sku = "TEST-SKU-" + UUID.randomUUID();
        int initialStock = 100;
        int concurrentBuyers = 500;
        stockService.seedStockIfAbsent(sku, initialStock);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger soldOutCount = new AtomicInteger();

        runConcurrently(concurrentBuyers, () -> {
            StockAndIdempotencyService.DecrementResult result = stockService.decrementStock(sku, 1);
            if (result == StockAndIdempotencyService.DecrementResult.OK) {
                successCount.incrementAndGet();
            } else if (result == StockAndIdempotencyService.DecrementResult.SOLD_OUT) {
                soldOutCount.incrementAndGet();
            }
        });

        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(soldOutCount.get()).isEqualTo(concurrentBuyers - initialStock);
        assertThat(stockService.remainingStock(sku)).isEqualTo(0L);
    }

    @Test
    void exactlyOneWinnerAmongConcurrentDuplicateRequests() throws InterruptedException {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        UUID orderId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes());
        int concurrentDuplicates = 50;

        AtomicInteger winners = new AtomicInteger();

        runConcurrently(concurrentDuplicates, () -> {
            UUID existingOwner = stockService.tryAcquireIdempotencyLock(idempotencyKey, orderId, Duration.ofSeconds(60));
            if (existingOwner == null) {
                winners.incrementAndGet();
            }
        });

        assertThat(winners.get()).isEqualTo(1);
    }

    private void runConcurrently(int times, Runnable task) throws InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(times);
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < times; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }
            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
        }
    }
}
