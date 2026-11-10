package dev.flashsale.tracking.config;

import dev.flashsale.common.rabbit.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * tracking-service's queue is non-durable/auto-delete: losing buffered tracking
 * events on a restart only costs UI niceness, never correctness, since it writes
 * nothing to any store.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(RabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    public Queue trackingQueue() {
        return QueueBuilder.nonDurable(RabbitTopology.QUEUE_TRACKING)
                .autoDelete()
                .build();
    }

    @Bean
    public Binding trackingQueueBinding(Queue trackingQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(trackingQueue).to(orderEventsExchange).with(RabbitTopology.ROUTING_KEY_ALL);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
