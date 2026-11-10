package dev.flashsale.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpKeyResolverTest {

    private static final InetSocketAddress CADDY = new InetSocketAddress("172.18.0.10", 54321);

    private final ClientIpKeyResolver resolver = new ClientIpKeyResolver();

    @Test
    void usesForwardedForInsteadOfProxyAddress() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/checkout")
                .remoteAddress(CADDY)
                .header("X-Forwarded-For", "203.0.113.7"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("203.0.113.7")
                .verifyComplete();
    }

    @Test
    void takesFirstEntryWhenMultipleHopsArePresent() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/checkout")
                .remoteAddress(CADDY)
                .header("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1"));

        assertThat(ClientIpKeyResolver.clientIp(exchange)).isEqualTo("203.0.113.7");
    }

    @Test
    void differentClientsBehindTheSameProxyGetDifferentKeys() {
        String first = ClientIpKeyResolver.clientIp(exchange(MockServerHttpRequest.post("/api/checkout")
                .remoteAddress(CADDY)
                .header("X-Forwarded-For", "203.0.113.7")));
        String second = ClientIpKeyResolver.clientIp(exchange(MockServerHttpRequest.post("/api/checkout")
                .remoteAddress(CADDY)
                .header("X-Forwarded-For", "198.51.100.23")));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void fallsBackToRemoteAddressWithoutForwardedFor() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/checkout")
                .remoteAddress(CADDY));

        assertThat(ClientIpKeyResolver.clientIp(exchange)).isEqualTo("172.18.0.10");
    }

    @Test
    void fallsBackToRemoteAddressWhenForwardedForIsBlank() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/checkout")
                .remoteAddress(CADDY)
                .header("X-Forwarded-For", "  "));

        assertThat(ClientIpKeyResolver.clientIp(exchange)).isEqualTo("172.18.0.10");
    }

    @Test
    void returnsUnknownWhenNothingIsAvailable() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/checkout"));

        assertThat(ClientIpKeyResolver.clientIp(exchange)).isEqualTo("unknown");
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }
}
