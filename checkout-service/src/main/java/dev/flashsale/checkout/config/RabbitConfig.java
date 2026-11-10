package dev.flashsale.checkout.config;

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
 * Checkout-service is the first publisher on the bus, so it owns declaring the
 * exchange and its dead-letter exchange/queue. Each consumer declares its own
 * queue + binding independently; RabbitMQ unifies declarations by name.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(RabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange orderEventsDeadLetterExchange() {
        return new TopicExchange(RabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderEventsDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue orderEventsDeadLetterQueue, TopicExchange orderEventsDeadLetterExchange) {
        return BindingBuilder.bind(orderEventsDeadLetterQueue)
                .to(orderEventsDeadLetterExchange)
                .with(RabbitTopology.DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
