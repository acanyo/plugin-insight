package com.xhhao.insight.tasks;

import com.xhhao.insight.diagnostics.DiagnosticRecords;
import com.xhhao.insight.halo.HaloHostContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.migration.Backup;

@Component
public class HaloTaskMonitor {
    private final HaloHostContext host;
    private final ReactiveExtensionClient extensions;
    private final DiagnosticRecords records;
    private volatile Snapshot latest = new Snapshot(0, false, "等待业务任务采样", List.of(), List.of());

    public HaloTaskMonitor(HaloHostContext host, ReactiveExtensionClient extensions, DiagnosticRecords records) {
        this.host = host; this.extensions = extensions; this.records = records;
    }

    public void sample(long now) {
        var pools = new ArrayList<Pool>();
        if (host.root() != null) {
            host.root().getBeansOfType(ThreadPoolTaskExecutor.class, false, false).forEach((name, bean) -> {
                try { pools.add(pool(name, bean.getThreadPoolExecutor())); } catch (IllegalStateException ignored) { }
            });
            host.root().getBeansOfType(ThreadPoolTaskScheduler.class, false, false).forEach((name, bean) -> {
                try { pools.add(pool(name, bean.getScheduledThreadPoolExecutor())); } catch (IllegalStateException ignored) { }
            });
            host.root().getBeansOfType(ThreadPoolExecutor.class, false, false).forEach((name, bean) -> pools.add(pool(name, bean)));
        }
        try {
            var backups = extensions.listAll(Backup.class, ListOptions.builder().build(),
                    Sort.by(Sort.Direction.DESC, "metadata.creationTimestamp"))
                .take(200).map(backup -> task(backup, now)).collectList().block(Duration.ofSeconds(4));
            latest = new Snapshot(now, true,
                "覆盖最近 200 个 Halo 备份任务及宿主公开线程池。计划队列含未来任务，不等于积压；无公开指标的第三方任务不在覆盖范围。",
                backups == null ? List.of() : backups, List.copyOf(pools));
        } catch (RuntimeException failure) {
            latest = new Snapshot(now, false, "Halo 备份任务查询失败，暂无法判断业务任务健康", List.of(), List.copyOf(pools));
        }
        records.save("tasks", Long.toString(now / 60_000), now, latest);
    }

    public Snapshot snapshot() { return latest; }
    public List<Snapshot> history() { return records.list("tasks", Snapshot.class, 60); }

    private static Task task(Backup backup, long now) {
        var status = backup.getStatus();
        long started = status == null || status.getStartTimestamp() == null ? 0 : status.getStartTimestamp().toEpochMilli();
        long completed = status == null || status.getCompletionTimestamp() == null ? 0 : status.getCompletionTimestamp().toEpochMilli();
        return new Task(backup.getMetadata().getName(), "Halo 数据备份",
            status == null || status.getPhase() == null ? "UNKNOWN" : status.getPhase().name(),
            started, completed, started == 0 ? 0 : Math.max(0, (completed == 0 ? now : completed) - started),
            status == null ? null : status.getFailureReason());
    }

    private static Pool pool(String name, ThreadPoolExecutor executor) {
        long ready = executor.getQueue().stream().limit(1000)
            .filter(task -> !(task instanceof java.util.concurrent.Delayed delayed)
                || delayed.getDelay(java.util.concurrent.TimeUnit.MILLISECONDS) <= 0).count();
        return new Pool(name, executor.getActiveCount(), executor.getPoolSize(), executor.getQueue().size(),
            ready, executor.getCompletedTaskCount(), executor.isShutdown());
    }

    public record Task(String id, String name, String state, long startedAtMs, long completedAtMs,
                       long durationMs, String failureReason) {}
    public record Pool(String name, int active, int threads, int scheduledOrQueued, long readyQueued,
                       long completed, boolean shutdown) {}
    public record Snapshot(long atMs, boolean available, String coverage, List<Task> tasks, List<Pool> pools) {}
}
