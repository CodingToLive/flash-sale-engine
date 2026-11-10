package dev.flashsale.checkout.service;

import dev.flashsale.checkout.config.AppProperties;
import dev.flashsale.checkout.domain.SaleConfig;
import dev.flashsale.checkout.dto.CheckoutRequest;
import dev.flashsale.checkout.dto.CheckoutResponse;
import dev.flashsale.checkout.dto.OrderResponse;
import dev.flashsale.checkout.dto.ProductResponse;
import dev.flashsale.checkout.error.OrderNotFoundException;
import dev.flashsale.checkout.error.ProductNotFoundException;
import dev.flashsale.checkout.error.SoldOutException;
import dev.flashsale.checkout.redis.StockAndIdempotencyService;
import dev.flashsale.checkout.redis.StockAndIdempotencyService.DecrementResult;
import dev.flashsale.checkout.repository.OrderRepository;
import dev.flashsale.checkout.repository.SaleConfigRepository;
import dev.flashsale.common.events.OrderLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The concurrency-safety core: an idempotency lock plus an atomic stock decrement,
 * both in Redis, with a Postgres unique-constraint insert as a second safety net.
 * See README "Highlights" for the full defense-in-depth rationale.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final AppProperties appProperties;
    private final SaleConfigRepository saleConfigRepository;
    private final OrderRepository orderRepository;
    private final StockAndIdempotencyService stockService;
    private final OrderEventPublisher eventPublisher;

    public CheckoutService(AppProperties appProperties,
                            SaleConfigRepository saleConfigRepository,
                            OrderRepository orderRepository,
                            StockAndIdempotencyService stockService,
                            OrderEventPublisher eventPublisher) {
        this.appProperties = appProperties;
        this.saleConfigRepository = saleConfigRepository;
        this.orderRepository = orderRepository;
        this.stockService = stockService;
        this.eventPublisher = eventPublisher;
    }

    public ProductResponse getProduct(String sku) {
        SaleConfig config = saleConfigRepository.findBySku(sku);
        long remaining = stockService.remainingStock(sku);
        return new ProductResponse(config.sku(), config.name(), config.priceCents(), remaining);
    }

    public OrderResponse getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public CheckoutResponse checkout(String idempotencyKey, CheckoutRequest request) {
        UUID orderId = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));

        UUID existingOwner = stockService.tryAcquireIdempotencyLock(
                idempotencyKey, orderId, Duration.ofSeconds(appProperties.idempotencyTtlSeconds()));
        if (existingOwner != null) {
            log.debug("Duplicate request for idempotency key {} -> order {}", idempotencyKey, existingOwner);
            return CheckoutResponse.duplicateIgnored(existingOwner);
        }

        DecrementResult result = stockService.decrementStock(request.sku(), request.quantity());
        if (result == DecrementResult.STOCK_NOT_SEEDED) {
            stockService.releaseIdempotencyLock(idempotencyKey);
            throw new ProductNotFoundException(request.sku());
        }
        if (result == DecrementResult.SOLD_OUT) {
            stockService.releaseIdempotencyLock(idempotencyKey);
            throw new SoldOutException(request.sku());
        }

        boolean inserted = orderRepository.insertIfAbsent(
                orderId, idempotencyKey, request.sku(), request.userId(), request.quantity());
        if (!inserted) {
            log.warn("Redis lock acquired but order {} already existed in Postgres - idempotency race caught by safety net", orderId);
            return CheckoutResponse.duplicateIgnored(orderId);
        }

        Instant now = Instant.now();
        eventPublisher.publish(OrderLifecycleEvent.created(
                orderId, idempotencyKey, request.sku(), request.userId(), request.quantity(), request.notifyEmail(), now));

        return CheckoutResponse.accepted(orderId);
    }

    public void resetDemo() {
        SaleConfig config = saleConfigRepository.findBySku(appProperties.sku());
        stockService.resetStock(config.sku(), config.initialStock());
        orderRepository.deleteAllForSku(config.sku());
        log.info("Demo reset: stock for {} restored to {}", config.sku(), config.initialStock());
    }
}
