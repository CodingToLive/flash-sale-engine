package dev.flashsale.checkout.controller;

import dev.flashsale.checkout.config.AppProperties;
import dev.flashsale.checkout.dto.CheckoutRequest;
import dev.flashsale.checkout.dto.CheckoutResponse;
import dev.flashsale.checkout.dto.OrderResponse;
import dev.flashsale.checkout.dto.ProductResponse;
import dev.flashsale.checkout.error.MissingIdempotencyKeyException;
import dev.flashsale.checkout.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final AppProperties appProperties;

    public CheckoutController(CheckoutService checkoutService, AppProperties appProperties) {
        this.checkoutService = checkoutService;
        this.appProperties = appProperties;
    }

    @GetMapping("/products/{sku}")
    public ProductResponse getProduct(@PathVariable String sku) {
        return checkoutService.getProduct(sku);
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        CheckoutResponse response = checkoutService.checkout(idempotencyKey, request);
        HttpStatus status = "ACCEPTED".equals(response.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return checkoutService.getOrder(orderId);
    }

    @PostMapping("/admin/reset")
    public ResponseEntity<Map<String, Boolean>> resetDemo(
            @RequestHeader(value = "X-Admin-Secret", required = false) String providedSecret) {
        if (providedSecret == null || !providedSecret.equals(appProperties.adminSecret())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin secret");
        }
        checkoutService.resetDemo();
        return ResponseEntity.ok(Map.of("reset", true));
    }
}
