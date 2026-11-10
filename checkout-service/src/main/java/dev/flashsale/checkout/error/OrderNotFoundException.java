package dev.flashsale.checkout.error;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Unknown order: " + orderId);
    }
}
