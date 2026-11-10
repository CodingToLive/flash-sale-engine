package dev.flashsale.tracking.relay;

import dev.flashsale.common.events.OrderHop;
import dev.flashsale.common.events.OrderLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Pure event-listener/relay: no writes, no business logic. Subscribes to every
 * order.* event and fans it out over SSE, keyed by idempotency key so the browser
 * can open its stream before an orderId even exists.
 */
@Component
public class OrderEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderEventRelay.class);
    private static final Duration IDLE_EVICTION_THRESHOLD = Duration.ofMinutes(5);
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(30);

    private final Map<String, Sinks.Many<OrderLifecycleEvent>> sinks = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastTouched = new ConcurrentHashMap<>();

    public Flux<OrderLifecycleEvent> subscribe(String idempotencyKey) {
        touch(idempotencyKey);
        return sinkFor(idempotencyKey).asFlux()
                .takeUntil(event -> isTerminal(event.hop()))
                .timeout(STREAM_TIMEOUT)
                .onErrorResume(TimeoutException.class, e -> Flux.empty());
    }

    public void publish(OrderLifecycleEvent event) {
        touch(event.idempotencyKey());
        Sinks.Many<OrderLifecycleEvent> sink = sinkFor(event.idempotencyKey());
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.debug("Dropped tracking event {} for order {} ({})", event.hop(), event.orderId(), result);
        }
    }

    @Scheduled(fixedRate = 60_000)
    void evictIdleSinks() {
        Instant cutoff = Instant.now().minus(IDLE_EVICTION_THRESHOLD);
        lastTouched.entrySet().removeIf(entry -> {
            boolean idle = entry.getValue().isBefore(cutoff);
            if (idle) {
                Sinks.Many<OrderLifecycleEvent> sink = sinks.remove(entry.getKey());
                if (sink != null) {
                    sink.tryEmitComplete();
                }
            }
            return idle;
        });
    }

    private Sinks.Many<OrderLifecycleEvent> sinkFor(String idempotencyKey) {
        return sinks.computeIfAbsent(idempotencyKey, key -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private void touch(String idempotencyKey) {
        lastTouched.put(idempotencyKey, Instant.now());
    }

    private static boolean isTerminal(OrderHop hop) {
        // CONFIRMED/FAILED are not terminal: notification-service always follows up
        // with NOTIFIED (for both outcomes), which is the true end of the pipeline.
        return hop == OrderHop.NOTIFIED;
    }
}
