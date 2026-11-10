package dev.flashsale.checkout.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the second idempotency layer (Postgres unique constraint on idempotency_key)
 * independently guarantees exactly-once insertion, in case the Redis lock were ever
 * bypassed or lost. See the Redis-level test for the primary fast-path guarantee.
 */
@Testcontainers
class OrderRepositoryConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flashsale")
            .withUsername("flashsale")
            .withPassword("flashsale");

    private DataSource dataSource;
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        dataSource = new SimpleDriverDataSource(new org.postgresql.Driver(),
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        orderRepository = new OrderRepository(new JdbcTemplate(dataSource));
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).update("DELETE FROM orders");
    }

    @Test
    void exactlyOneRowSurvivesConcurrentInsertsWithSameIdempotencyKey() throws InterruptedException {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        UUID orderId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes());
        int concurrentDuplicates = 50;

        AtomicInteger insertedCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentDuplicates);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < concurrentDuplicates; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (orderRepository.insertIfAbsent(orderId, idempotencyKey, "FLASH-SALE-ITEM", "user-1", 1)) {
                            insertedCount.incrementAndGet();
                        }
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

        assertThat(insertedCount.get()).isEqualTo(1);
        assertThat(orderRepository.findById(orderId)).isPresent();
    }
}
