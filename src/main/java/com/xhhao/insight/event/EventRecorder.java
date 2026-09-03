package com.xhhao.insight.event;

import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.model.MonitorEvent;
import com.xhhao.insight.storage.repository.MonitorEventRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventRecorder {

    private final MonitorEventRepository repository;
    private final HistoryWriteGate writeGate;
    private final Clock clock;

    @Autowired
    public EventRecorder(MonitorEventRepository repository, HistoryWriteGate writeGate) {
        this(repository, writeGate, Clock.systemUTC());
    }

    EventRecorder(MonitorEventRepository repository, HistoryWriteGate writeGate, Clock clock) {
        this.repository = repository;
        this.writeGate = writeGate;
        this.clock = clock;
    }

    public void record(String type, String severity, String source, String title, String summary) {
        record(type, severity, source, title, summary, null);
    }

    public void record(String type, String severity, String source, String title, String summary,
        String detailsJson) {
        long now = clock.millis();
        recordBatch(List.of(new EventOccurrence(
            type, severity, source, title, summary, type + ':' + source + ':' + title,
            now, now, 1, detailsJson)));
    }

    /** Persists a bounded group of already-coalesced event occurrences in one transaction. */
    public void recordBatch(List<EventOccurrence> occurrences) {
        if (occurrences == null || occurrences.isEmpty() || !writeGate.allowsWrite()) {
            return;
        }
        try {
            repository.saveBatch(occurrences.stream().map(occurrence -> new MonitorEvent(
                UUID.randomUUID().toString(), occurrence.lastSeenAtMs(), occurrence.type(),
                occurrence.severity(), occurrence.source(), occurrence.title(),
                occurrence.summary(), occurrence.fingerprint(), occurrence.firstSeenAtMs(),
                occurrence.lastSeenAtMs(), occurrence.occurrenceCount(),
                occurrence.detailsJson())).toList());
        } catch (RuntimeException error) {
            log.warn("Failed to persist {} Insight timeline event occurrence(s)",
                occurrences.size(), error);
        }
    }

    public void recordFailure(
        String type,
        String severity,
        String source,
        String title,
        String summary,
        String operation,
        Throwable failure
    ) {
        record(type, severity, source, title, summary,
            MonitoringFailureDetails.capture(operation, failure).toJson());
    }

    public record EventOccurrence(
        String type,
        String severity,
        String source,
        String title,
        String summary,
        String fingerprint,
        long firstSeenAtMs,
        long lastSeenAtMs,
        long occurrenceCount,
        String detailsJson
    ) {}
}
