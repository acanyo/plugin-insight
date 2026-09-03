package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ApiTimeRangeTest {

    @Test
    void shouldParseSupportedRanges() {
        long now = Duration.ofDays(100).toMillis();

        ApiTimeRange range = ApiTimeRange.parse("7d", now);

        assertThat(range.name()).isEqualTo("7d");
        assertThat(range.fromMs()).isEqualTo(now - Duration.ofDays(7).toMillis());
        assertThat(range.toMs()).isEqualTo(now + 1);
    }

    @Test
    void shouldRejectUnknownRanges() {
        assertThatThrownBy(() -> ApiTimeRange.parse("forever", 100_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported range");
    }
}
