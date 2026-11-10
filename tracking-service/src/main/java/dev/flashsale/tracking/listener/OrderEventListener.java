package dev.flashsale.tracking.listener;

import dev.flashsale.common.events.OrderLifecycleEvent;
import dev.flashsale.common.rabbit.RabbitTopology;
import dev.flashsale.tracking.relay.OrderEventRelay;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final OrderEventRelay relay;

    public OrderEventListener(OrderEventRelay relay) {
        this.relay = relay;
    }

    @RabbitListener(queues = RabbitTopology.QUEUE_TRACKING)
    public void onOrderEvent(OrderLifecycleEvent event) {
        relay.publish(event);
    }
}
