package com.xhhao.insight.diagnostics.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.diagnostics.DiagnosticConflictException;
import com.xhhao.insight.diagnostics.HaloLogService.Entry;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class LogQuerySnapshotsTest {
    @Test
    void ExpiresQueriesAndRejectsChangedFilters() {
        var now = new AtomicLong(1000);
        var clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        var snapshots = new LogQuerySnapshots(clock);
        var query = new LogQuerySnapshots.Query(0, "all", "", "alpha");
        var snapshot =
                snapshots.put(
                        query,
                        List.of("halo.log"),
                        List.of(new Entry("INFO", "ready")),
                        List.of(),
                        null,
                        false,
                        true,
                        "");
        assertThat(snapshots.get(snapshot.id(), query)).isSameAs(snapshot);
        assertThatThrownBy(
                        () ->
                                snapshots.get(
                                        snapshot.id(),
                                        new LogQuerySnapshots.Query(0, "ERROR", "", "alpha")))
                .isInstanceOf(IllegalArgumentException.class);
        now.addAndGet(300_000);
        assertThatThrownBy(() -> snapshots.get(snapshot.id(), query))
                .isInstanceOf(DiagnosticConflictException.class);
    }

    @Test
    void EvictsOldSessionsAndBoundsRetainedCharacters() {
        var snapshots = new LogQuerySnapshots();
        var query = new LogQuerySnapshots.Query(0, "all", "", "");
        var first = snapshots.put(query, List.of(), List.of(), List.of(), null, false, true, "");
        for (int i = 0; i < 16; i++)
            snapshots.put(query, List.of(), List.of(), List.of(), null, false, true, "");
        assertThatThrownBy(() -> snapshots.get(first.id(), query))
                .isInstanceOf(DiagnosticConflictException.class);
        var entries =
                java.util.stream.IntStream.range(0, 100)
                        .mapToObj(i -> new Entry("INFO", "x".repeat(16_384)))
                        .toList();
        var bounded = snapshots.put(query, List.of(), entries, List.of(), null, false, true, "");
        assertThat(bounded.entries().stream().mapToInt(entry -> entry.text().length()).sum())
                .isLessThanOrEqualTo(512 * 1024);
        assertThat(bounded.truncated()).isTrue();
    }
}
