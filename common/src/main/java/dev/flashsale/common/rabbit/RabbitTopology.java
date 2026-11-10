package dev.flashsale.common.rabbit;

/**
 * Single source of truth for exchange/queue/routing-key names so they can't drift
 * between services that each declare their own bindings independently.
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    public static final String EXCHANGE = "order.events";
    public static final String DEAD_LETTER_EXCHANGE = "order.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "order.events.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "#";

    public static final String ROUTING_KEY_CREATED = "order.created";
    public static final String ROUTING_KEY_PROCESSING = "order.processing";
    public static final String ROUTING_KEY_CONFIRMED = "order.confirmed";
    public static final String ROUTING_KEY_FAILED = "order.failed";
    public static final String ROUTING_KEY_NOTIFIED = "order.notified";
    public static final String ROUTING_KEY_ALL = "order.#";

    public static final String QUEUE_PROCESSING = "order.processing.q";
    public static final String QUEUE_NOTIFICATION = "order.notification.q";
    public static final String QUEUE_TRACKING = "order.tracking.q";

    public static String deadLetterRoutingKeyFor(String queueName) {
        return queueName + ".dlq";
    }
}
