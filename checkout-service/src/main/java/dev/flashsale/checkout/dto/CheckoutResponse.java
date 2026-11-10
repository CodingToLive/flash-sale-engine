package dev.flashsale.checkout.dto;

import java.util.UUID;

public record CheckoutResponse(UUID orderId, String status) {

    public static CheckoutResponse accepted(UUID orderId) {
        return new CheckoutResponse(orderId, "ACCEPTED");
    }

    public static CheckoutResponse duplicateIgnored(UUID orderId) {
        return new CheckoutResponse(orderId, "DUPLICATE_IGNORED");
    }
}
