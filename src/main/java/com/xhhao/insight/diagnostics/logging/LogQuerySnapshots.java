package com.xhhao.insight.diagnostics.logging;

import com.xhhao.insight.diagnostics.DiagnosticConflictException;
import com.xhhao.insight.diagnostics.HaloLogService.Entry;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Immutable query sessions, bounded by count and retained text; no file handles or raw secrets. */
@Component
public class LogQuerySnapshots {
    private static final long TTL_MS = 300_000;
    private static final int MAX_SESSIONS = 16;
    private static final long MAX_TOTAL_CHARACTERS = 4 * 1024 * 1024;
    private static final long MAX_SESSION_CHARACTERS = 512 * 1024;
    private final Clock clock;
    private final LinkedHashMap<String, Snapshot> sessions = new LinkedHashMap<>();

    public LogQuerySnapshots() {
        this(Clock.systemUTC());
    }

    LogQuerySnapshots(Clock clock) {
        this.clock = clock;
    }

    public synchronized Snapshot put(
            Query query,
            List<String> sources,
            List<Entry> entries,
            List<PluginLogClassIndex.PluginOption> plugins,
            BoundedLogFileReader.Cursor cursor,
            boolean truncated,
            boolean indexComplete,
            String notice) {
        prune();
        int from = Math.max(0, entries.size() - 10_000);
        long characters =
                entries.subList(from, entries.size()).stream()
                        .mapToLong(entry -> entry.text().length())
                        .sum();
        while (characters > MAX_SESSION_CHARACTERS && from < entries.size())
            characters -= entries.get(from++).text().length();
        var snapshot =
                new Snapshot(
                        UUID.randomUUID().toString(),
                        clock.millis(),
                        query,
                        List.copyOf(sources),
                        List.copyOf(entries.subList(from, entries.size())),
                        List.copyOf(plugins),
                        cursor,
                        truncated || from > 0,
                        indexComplete,
                        from > 0 ? notice + "快照达到保留上限，已移除最早记录。" : notice);
        sessions.put(snapshot.id(), snapshot);
        while (sessions.size() > MAX_SESSIONS || characters() > MAX_TOTAL_CHARACTERS) {
            sessions.remove(sessions.keySet().iterator().next());
        }
        return snapshot;
    }

    public synchronized Snapshot get(String id, Query query) {
        prune();
        var snapshot = sessions.get(id);
        if (snapshot == null) throw new DiagnosticConflictException("日志查询快照已过期或被回收，请重新查询");
        if (!snapshot.query().equals(query)) throw new IllegalArgumentException("筛选条件已变更，请重新查询");
        return snapshot;
    }

    private void prune() {
        sessions.values().removeIf(snapshot -> clock.millis() - snapshot.capturedAtMs() >= TTL_MS);
    }

    private long characters() {
        return sessions.values().stream()
                .flatMap(snapshot -> snapshot.entries().stream())
                .mapToLong(entry -> entry.text().length())
                .sum();
    }

    public record Query(int source, String level, String keyword, String pluginId) {}

    public record Snapshot(
            String id,
            long capturedAtMs,
            Query query,
            List<String> sources,
            List<Entry> entries,
            List<PluginLogClassIndex.PluginOption> plugins,
            BoundedLogFileReader.Cursor cursor,
            boolean truncated,
            boolean indexComplete,
            String notice) {}
}
