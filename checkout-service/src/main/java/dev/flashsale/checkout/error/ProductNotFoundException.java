package dev.flashsale.checkout.error;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String sku) {
        super("Unknown SKU: " + sku);
    }
}
