package dev.flashsale.notification.listener;

import dev.flashsale.common.events.OrderLifecycleEvent;
import dev.flashsale.common.rabbit.RabbitTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(OrderLifecycleEvent event) {
        log.info("Publishing {} for order {}", event.hop(), event.orderId());
        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, event.hop().routingKey(), event);
    }
}
