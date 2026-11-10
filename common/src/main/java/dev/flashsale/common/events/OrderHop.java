package dev.flashsale.common.events;

/**
 * Every stage an order passes through, in pipeline order. The frontend's live
 * pipeline visualization lights up nodes strictly in this order as events arrive.
 */
public enum OrderHop {
    CREATED,
    PROCESSING,
    CONFIRMED,
    FAILED,
    NOTIFIED;

    public String routingKey() {
        return "order." + name().toLowerCase();
    }
}
