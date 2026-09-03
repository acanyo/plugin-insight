package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;

class InsightApiInputTest {

    @Test
    void shouldRejectOversizedAndControlCharacterInputs() {
        ServerRequest oversized = request("/events?cursor=" + "a".repeat(513));

        assertThatThrownBy(() -> InsightApiInput.optionalQuery(
            oversized, "cursor", InsightApiInput.CURSOR_MAX_LENGTH))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maximum length");
    }

    @Test
    void shouldParseBoundedIntegerWithoutRetainingUnderlyingCause() {
        assertThat(InsightApiInput.integerQuery(request("/alerts?limit=50"), "limit", 10))
            .isEqualTo(50);
        assertThatThrownBy(() -> InsightApiInput.integerQuery(
            request("/alerts?limit=invalid"), "limit", 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be an integer")
            .hasNoCause();
    }

    private static ServerRequest request(String uri) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(uri));
        return ServerRequest.create(exchange, java.util.List.of());
    }
}
