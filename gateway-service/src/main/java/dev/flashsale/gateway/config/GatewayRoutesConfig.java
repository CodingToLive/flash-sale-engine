package dev.flashsale.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

/**
 * Single public entry point: routes to checkout-service and tracking-service, with a
 * per-IP rate limiter on the checkout POST only. Limits are deliberately generous
 * (burst 300) so the frontend's "simulate traffic spike" demo (~200 concurrent requests)
 * exercises a real, visible capability instead of just tripping 429s.
 */
@Configuration
public class GatewayRoutesConfig {

    @Value("${app.checkout-service-uri}")
    private String checkoutServiceUri;

    @Value("${app.tracking-service-uri}")
    private String trackingServiceUri;

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .defaultIfEmpty("unknown");
    }

    @Bean
    public RedisRateLimiter checkoutRateLimiter() {
        return new RedisRateLimiter(100, 300, 1);
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, RedisRateLimiter checkoutRateLimiter, KeyResolver ipKeyResolver) {
        return builder.routes()
                .route("checkout-post", r -> r.path("/api/checkout")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f.requestRateLimiter(c -> c
                                        .setRateLimiter(checkoutRateLimiter)
                                        .setKeyResolver(ipKeyResolver))
                                .addRequestHeader("X-Gateway", "flash-sale-gateway"))
                        .uri(checkoutServiceUri))
                .route("checkout-api", r -> r.path("/api/products/**", "/api/orders/**", "/api/admin/**")
                        .uri(checkoutServiceUri))
                .route("tracking-api", r -> r.path("/api/tracking/**")
                        .uri(trackingServiceUri))
                .build();
    }
}
