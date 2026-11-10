package dev.flashsale.orderprocessing.listener;

import dev.flashsale.common.events.OrderHop;
import dev.flashsale.common.events.OrderLifecycleEvent;
import dev.flashsale.common.rabbit.RabbitTopology;
import dev.flashsale.orderprocessing.config.AppProperties;
import dev.flashsale.orderprocessing.redis.RestockService;
import dev.flashsale.orderprocessing.repository.OrderStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This is the "processing" step the broker decouples checkout from: checkout
 * already returned 202 to the caller before any of this runs.
 */
@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final AppProperties appProperties;
    private final OrderStatusRepository orderStatusRepository;
    private final RestockService restockService;
    private final OrderEventPublisher eventPublisher;

    public OrderCreatedListener(AppProperties appProperties,
                                 OrderStatusRepository orderStatusRepository,
                                 RestockService restockService,
                                 OrderEventPublisher eventPublisher) {
        this.appProperties = appProperties;
        this.orderStatusRepository = orderStatusRepository;
        this.restockService = restockService;
        this.eventPublisher = eventPublisher;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE_PROCESSING)
    public void onOrderCreated(OrderLifecycleEvent event) throws InterruptedException {
        if (event.hop() != OrderHop.CREATED) {
            log.warn("Ignoring unexpected hop {} on processing queue for order {}", event.hop(), event.orderId());
            return;
        }

        log.info("Processing order {} ({} x {})", event.orderId(), event.quantity(), event.sku());
        eventPublisher.publish(OrderLifecycleEvent.processing(event, Instant.now()));

        simulateFulfillmentWork();

        boolean simulatedFailure = ThreadLocalRandom.current().nextDouble() < appProperties.failureRate();
        if (simulatedFailure) {
            handleFailure(event);
        } else {
            handleSuccess(event);
        }
    }

    private void handleSuccess(OrderLifecycleEvent event) {
        boolean updated = orderStatusRepository.markConfirmed(event.orderId());
        if (!updated) {
            log.info("Order {} already processed (redelivered message) - skipping", event.orderId());
            return;
        }
        eventPublisher.publish(OrderLifecycleEvent.confirmed(event, Instant.now()));
    }

    private void handleFailure(OrderLifecycleEvent event) {
        String reason = "Simulated fulfillment failure";
        boolean updated = orderStatusRepository.markFailed(event.orderId(), reason);
        if (!updated) {
            log.info("Order {} already processed (redelivered message) - skipping", event.orderId());
            return;
        }
        restockService.restock(event.sku(), event.quantity());
        eventPublisher.publish(OrderLifecycleEvent.failed(event, reason, Instant.now()));
    }

    private void simulateFulfillmentWork() throws InterruptedException {
        long delay = ThreadLocalRandom.current().nextLong(
                appProperties.minProcessingDelayMs(), appProperties.maxProcessingDelayMs() + 1);
        Thread.sleep(delay);
    }
}
