package dev.flashsale.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate-limit key = the real client IP. The gateway only ever sees Caddy's container
 * address as the TCP peer, so the client IP has to come from X-Forwarded-For, which
 * Caddy sets. Caddy ignores any X-Forwarded-For sent by the client (it trusts no
 * upstream proxies), so the first entry is the address Caddy itself saw and can't be
 * spoofed. That only holds while Caddy is the sole ingress - the gateway port must
 * never be exposed publicly.
 */
@Component
public class ClientIpKeyResolver implements KeyResolver {

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just(clientIp(exchange));
    }

    static String clientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
