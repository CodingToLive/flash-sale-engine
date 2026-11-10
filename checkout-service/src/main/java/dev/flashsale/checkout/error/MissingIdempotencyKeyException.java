package dev.flashsale.checkout.error;

public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException() {
        super("Missing required Idempotency-Key header");
    }
}
