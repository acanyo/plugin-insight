package com.xhhao.insight.diagnostics;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import org.springframework.stereotype.Component;

@Component
public class JfrDiagnostics {
    private final DiagnosticRecords records;
    private final Path directory;
    private Recording recording;
    private Session active;
    private long lastCleanupMs;

    public JfrDiagnostics(DiagnosticRecords records, SqliteConnectionManager database) {
        this.records = records; directory = database.databasePath().getParent().resolve("diagnostics");
    }

    public synchronized Session start(int seconds) throws Exception {
        if (!FlightRecorder.isAvailable()) { throw new DiagnosticConflictException("当前 JVM 不支持 JFR"); }
        if (seconds != 30 && seconds != 60 && seconds != 120) { throw new IllegalArgumentException("录制时长必须为 30、60 或 120 秒"); }
        if (recording != null) { throw new DiagnosticConflictException("已有 JFR 录制进行中"); }
        Files.createDirectories(directory);
        cleanup(2);
        long now = System.currentTimeMillis();
        var value = new Session(UUID.randomUUID().toString(), "recording", now, now + seconds * 1000L, 0, null, "录制中");
        records.save("jfr", value.id(), now, value);
        var next = new Recording();
        try {
            // Explicit allow-list avoids collecting environment variables, system properties or request payloads.
            next.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
            next.enable("jdk.ObjectAllocationSample").with("throttle", "100/s").withStackTrace();
            next.enable("jdk.GarbageCollection");
            next.enable("jdk.GCHeapSummary");
            next.enable("jdk.OldObjectSample").withStackTrace();
            next.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(20)).withStackTrace();
            next.setMaxSize(32 * 1024 * 1024L);
            next.setDuration(Duration.ofSeconds(seconds));
            next.setName("Insight-" + value.id());
            next.start(); recording = next; active = value;
            return value;
        } catch (Exception error) {
            next.close(); records.save("jfr", value.id(), now,
                new Session(value.id(), "failed", now, now, 0, null, "当前 JVM 未能开始 JFR 录制"));
            throw error;
        }
    }

    public synchronized void tick() throws Exception {
        long now = System.currentTimeMillis();
        if (now - lastCleanupMs >= 60_000) {
            cleanup(3);
            lastCleanupMs = now;
        }
        if (recording == null || recording.getState() != RecordingState.STOPPED) { return; }
        var current = active;
        try {
            Path file = path(current.id()); recording.dump(file);
            var result = JfrAnalysis.read(file);
            records.save("jfr", current.id(), System.currentTimeMillis(), new Session(current.id(), "completed",
                current.startedAtMs(), current.endsAtMs(), Files.size(file), result,
                "录制已完成。CPU/分配热点为采样证据，OldObjectSample 也不能单独证明插件内存泄漏。"));
        } catch (Exception failure) {
            records.save("jfr", current.id(), System.currentTimeMillis(), new Session(current.id(), "failed",
                current.startedAtMs(), current.endsAtMs(), 0, null, "录制导出或分析失败，请检查磁盘空间和 JVM 能力"));
            throw failure;
        } finally { recording.close(); recording = null; active = null; }
    }

    public synchronized void stop() {
        if (recording != null) {
            recording.close(); recording = null;
            try { records.save("jfr", active.id(), System.currentTimeMillis(), new Session(active.id(), "interrupted",
                active.startedAtMs(), System.currentTimeMillis(), 0, null, "Insight 停止，录制中断")); }
            catch (RuntimeException ignored) { }
            active = null;
        }
    }

    public synchronized void recover() throws Exception {
        for (var session : list()) {
            if ("recording".equals(session.state())) {
                records.save("jfr", session.id(), System.currentTimeMillis(), new Session(session.id(), "interrupted",
                    session.startedAtMs(), session.endsAtMs(), 0, null, "进程重启，录制中断"));
            }
        }
        cleanup(3);
    }

    public List<Session> list() { return records.list("jfr", Session.class, 30); }
    public boolean available() { return FlightRecorder.isAvailable(); }

    public Path download(String id) {
        var session = list().stream().filter(s -> s.id().equals(id) && "completed".equals(s.state()))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("录制未完成或已过期"));
        Path file = path(session.id());
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("录制文件已清理，仅保留分析摘要");
        }
        return file;
    }

    private Path path(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) { throw new IllegalArgumentException("录制编号无效"); }
        return directory.resolve(id + ".jfr");
    }

    private void cleanup(int maximumFiles) throws Exception {
        if (!Files.isDirectory(directory)) { return; }
        try (var files = Files.list(directory)) {
            var owned = files.filter(p -> p.getFileName().toString().matches("[a-f0-9-]{36}\\.jfr"))
                .filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                .sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified())).toList();
            for (int i = 0; i < owned.size(); i++) {
                if (i >= maximumFiles || owned.get(i).toFile().lastModified() < System.currentTimeMillis() - 3 * 86_400_000L) {
                    Files.deleteIfExists(owned.get(i));
                }
            }
        }
    }

    public record Session(String id, String state, long startedAtMs, long endsAtMs, long bytes,
                          JfrAnalysis.Result analysis, String message) {}
}
