package dev.flashsale.tracking.controller;

import dev.flashsale.common.events.OrderLifecycleEvent;
import dev.flashsale.tracking.relay.OrderEventRelay;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class TrackingController {

    private final OrderEventRelay relay;

    public TrackingController(OrderEventRelay relay) {
        this.relay = relay;
    }

    @GetMapping(value = "/api/tracking/{idempotencyKey}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderLifecycleEvent> streamOrderEvents(@PathVariable String idempotencyKey) {
        return relay.subscribe(idempotencyKey);
    }
}
