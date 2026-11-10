package dev.flashsale.notification.config;

import dev.flashsale.common.rabbit.RabbitTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public Queue notificationQueue() {
        return QueueBuilder.durable(RabbitTopology.QUEUE_NOTIFICATION)
                .withArgument("x-dead-letter-exchange", RabbitTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitTopology.deadLetterRoutingKeyFor(RabbitTopology.QUEUE_NOTIFICATION))
                .build();
    }

    @Bean
    public Binding notificationConfirmedBinding(Queue notificationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(orderEventsExchange).with(RabbitTopology.ROUTING_KEY_CONFIRMED);
    }

    @Bean
    public Binding notificationFailedBinding(Queue notificationQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(orderEventsExchange).with(RabbitTopology.ROUTING_KEY_FAILED);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange orderEventsDeadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(orderEventsDeadLetterExchange).with(RabbitTopology.DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
