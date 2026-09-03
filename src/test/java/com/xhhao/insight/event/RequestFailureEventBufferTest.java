package com.xhhao.insight.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.metrics.RequestMetricDimensions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RequestFailureEventBufferTest {

    @Test
    void coalescesEquivalentFailuresWithoutPersistingExceptionMessages() {
        RequestFailureEventBuffer buffer = buffer();
        RequestMetricDimensions dimensions = RequestMetricDimensions.normalize(
            "PluginPhotos", "/apis/photos/{name}", "get");

        buffer.record(dimensions, 500, IllegalStateException.class.getName());
        buffer.record(dimensions, 500, IllegalStateException.class.getName());

        assertThat(buffer.drain()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("request");
            assertThat(event.source()).isEqualTo("PluginPhotos");
            assertThat(event.occurrenceCount()).isEqualTo(2);
            assertThat(event.summary()).contains("GET", "/apis/photos/{name}", "500");
            assertThat(event.detailsJson())
                .contains("java.lang.IllegalStateException")
                .doesNotContain("private service url");
        });
        assertThat(buffer.drain()).isEmpty();
    }

    @Test
    void foldsExcessUniqueFailuresIntoOneBoundedOverflowOccurrence() {
        RequestFailureEventBuffer buffer = buffer();

        for (int index = 0; index < RequestFailureEventBuffer.MAX_UNIQUE_FAILURES + 7; index++) {
            buffer.record(RequestMetricDimensions.normalize(
                "halo-core", "/route/" + index, "GET"), 500, null);
        }

        assertThat(buffer.drain())
            .hasSize(RequestFailureEventBuffer.MAX_UNIQUE_FAILURES + 1)
            .filteredOn(event -> event.source().equals("_overflow"))
            .singleElement()
            .satisfies(event -> assertThat(event.occurrenceCount()).isEqualTo(7));
    }

    private static RequestFailureEventBuffer buffer() {
        return new RequestFailureEventBuffer(
            Clock.fixed(Instant.ofEpochMilli(12_345), ZoneOffset.UTC));
    }
}
