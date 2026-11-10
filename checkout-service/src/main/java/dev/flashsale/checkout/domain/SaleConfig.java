package dev.flashsale.checkout.domain;

public record SaleConfig(String sku, String name, int initialStock, int priceCents) {
}
