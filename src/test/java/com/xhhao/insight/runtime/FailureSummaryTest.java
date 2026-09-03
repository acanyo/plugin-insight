package com.xhhao.insight.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureSummaryTest {

    @Test
    void exposesRootExceptionTypeWithoutLeakingMessage() {
        RuntimeException failure = new RuntimeException(
            "jdbc:postgresql://user:secret@example.test/app?password=hunter2",
            new IllegalStateException("/srv/halo/insight.sqlite"));

        assertThat(FailureSummary.of(failure))
            .isEqualTo("IllegalStateException")
            .doesNotContain("secret", "password", "/srv");
    }

    @Test
    void handlesNullAndMissingMessage() {
        assertThat(FailureSummary.of(null)).isEqualTo("Unknown failure");
        assertThat(FailureSummary.of(new RuntimeException())).isEqualTo("RuntimeException");
    }
}
