package dev.flashsale.checkout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * notifyEmail is optional and opt-in only: a visitor who wants to actually receive the
 * order notification can supply their address, but the demo never requires it.
 */
public record CheckoutRequest(
        @NotBlank String sku,
        @NotBlank String userId,
        @Min(1) int quantity,
        @Email String notifyEmail
) {
}
