package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.xhhao.insight.metrics.LatencyBuckets;
import com.xhhao.insight.metrics.RequestMetric;
import com.xhhao.insight.storage.model.RequestMetricMinute;
import com.xhhao.insight.storage.repository.MetricBatchRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompletedMetricBufferTest {

    @Test
    void retainsFailedBatchAndRetriesItWithLaterMetrics() {
        MetricBatchRepository repository = mock(MetricBatchRepository.class);
        doThrow(new RuntimeException("temporary SQLite failure"))
            .doNothing()
            .when(repository).save(anyList(), anyList());
        CompletedMetricBuffer buffer = new CompletedMetricBuffer(repository);

        buffer.enqueue(List.of(metric(60_000, "/first")));
        assertThatThrownBy(buffer::flush).hasMessage("temporary SQLite failure");
        assertThat(buffer.pendingCount()).isEqualTo(1);

        buffer.enqueue(List.of(metric(120_000, "/second")));
        buffer.flush();

        assertThat(buffer.pendingCount()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestMetricMinute>> requests = ArgumentCaptor.forClass(List.class);
        verify(repository, org.mockito.Mockito.times(2)).save(requests.capture(), anyList());
        assertThat(requests.getAllValues().get(1))
            .extracting(RequestMetricMinute::route)
            .containsExactly("/first", "/second");
    }

    @Test
    void boundsPendingMetricsAndDropsOldestBeforeChunkedReplay() {
        MetricBatchRepository repository = mock(MetricBatchRepository.class);
        CompletedMetricBuffer buffer = new CompletedMetricBuffer(repository, 2, 2);

        assertThat(buffer.enqueue(List.of(
            metric(60_000, "/first"),
            metric(120_000, "/second"),
            metric(180_000, "/third")
        ))).isEqualTo(1);
        assertThat(buffer.pendingCount()).isEqualTo(2);
        assertThat(buffer.droppedCount()).isEqualTo(1);

        buffer.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestMetricMinute>> requests = ArgumentCaptor.forClass(List.class);
        verify(repository).save(requests.capture(), anyList());
        assertThat(requests.getValue())
            .extracting(RequestMetricMinute::route)
            .containsExactly("/second", "/third");
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    void countsMetricsIntentionallyDiscardedDuringCapacityPause() {
        CompletedMetricBuffer buffer = new CompletedMetricBuffer(
            mock(MetricBatchRepository.class), 2, 1);

        assertThat(buffer.discard(3)).isEqualTo(3);
        assertThat(buffer.droppedCount()).isEqualTo(3);
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    void discardingPendingMetricsClearsBackfillAndCountsTheLoss() {
        CompletedMetricBuffer buffer = new CompletedMetricBuffer(
            mock(MetricBatchRepository.class), 3, 1);
        buffer.enqueue(List.of(metric(60_000, "/first"), metric(120_000, "/second")));

        assertThat(buffer.discardPending()).isEqualTo(2);
        assertThat(buffer.pendingCount()).isZero();
        assertThat(buffer.droppedCount()).isEqualTo(2);
    }

    private static RequestMetric metric(long bucketStartMs, String route) {
        long[] histogram = new long[LatencyBuckets.size()];
        histogram[0] = 1;
        return new RequestMetric(
            bucketStartMs, "halo-core", route, "GET", 2, 1, 0, 5, 5, histogram);
    }
}
