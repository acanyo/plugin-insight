package com.xhhao.insight.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MonitoringFailureDetailsTest {

    @Test
    void capturesStableTypesWithoutPersistingExceptionMessages() {
        Throwable failure = new IllegalStateException(
            "secret database url", new IllegalArgumentException("private request path"));

        String json = MonitoringFailureDetails.capture("runtime_refresh", failure).toJson();

        assertThat(json)
            .contains("\"kind\":\"monitoring_failure\"")
            .contains("\"operation\":\"runtime_refresh\"")
            .contains("java.lang.IllegalStateException")
            .contains("java.lang.IllegalArgumentException")
            .doesNotContain("secret database url")
            .doesNotContain("private request path");
    }
}
