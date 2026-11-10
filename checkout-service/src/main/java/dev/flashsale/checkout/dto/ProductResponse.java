package dev.flashsale.checkout.dto;

public record ProductResponse(String sku, String name, int priceCents, long remainingStock) {
}
