package dev.flashsale.checkout.error;

public class SoldOutException extends RuntimeException {
    public SoldOutException(String sku) {
        super("Sold out: " + sku);
    }
}
