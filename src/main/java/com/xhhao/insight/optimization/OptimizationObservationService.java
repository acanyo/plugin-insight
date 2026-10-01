package com.xhhao.insight.optimization;

import com.xhhao.insight.diagnostics.DiagnosticRecords;
import com.xhhao.insight.diagnostics.DiagnosticConflictException;
import com.xhhao.insight.event.EventRecorder;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

/** Records observation only. Plugin state changes always use Halo's permission-checked API. */
@Component
public class OptimizationObservationService {
    private static final String KIND = "optimization-observation";
    private final DiagnosticRecords records;
    private final ObservationMetrics metrics;
    private final ReactiveExtensionClient plugins;
    private final EventRecorder events;

    public OptimizationObservationService(DiagnosticRecords records, ObservationMetrics metrics,
        ReactiveExtensionClient plugins, EventRecorder events) {
        this.records = records; this.metrics = metrics; this.plugins = plugins; this.events = events;
    }

    public synchronized Observation prepare(String pluginId) {
        if (pluginId == null || !pluginId.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}") || "insight".equals(pluginId)) {
            throw new IllegalArgumentException("请选择其他已启用插件");
        }
        if (list().stream().anyMatch(Observation::active)) { throw new DiagnosticConflictException("已有优化观察进行中，请先等待其结束"); }
        var plugin = plugin(pluginId);
        if (!Boolean.TRUE.equals(plugin.getSpec().getEnabled())) { throw new DiagnosticConflictException("插件已经停用"); }
        long now = System.currentTimeMillis(); long end = now / 60_000 * 60_000;
        var baseline = metrics.summarize(end - 300_000, end);
        var value = new Observation(UUID.randomUUID().toString(), pluginId, plugin.getSpec().getDisplayName(),
            "prepared", now, 0, 0, baseline, null, "已记录停用前 5 分钟基线，等待确认停用成功");
        save(value);
        return value;
    }

    public synchronized Observation confirm(String id) {
        var previous = find(id);
        if (!"prepared".equals(previous.state())) { return previous; }
        if (Boolean.TRUE.equals(plugin(previous.pluginId()).getSpec().getEnabled())) {
            throw new DiagnosticConflictException("插件仍启用，无法开始停用后观察");
        }
        long now = System.currentTimeMillis();
        long from = (now / 60_000 + 1) * 60_000;
        var current = new Observation(previous.id(), previous.pluginId(), previous.displayName(),
            "observing", previous.createdAtMs(), from, from + 300_000, previous.before(), null,
            "正在收集停用后 5 个完整分钟；请求量变化也会影响对比，结果不代表因果证明");
        save(current); return current;
    }

    public synchronized Observation cancel(String id) {
        var old = find(id);
        if (!old.active()) { return old; }
        var value = old.finish("cancelled", null, "操作未完成或观察已取消");
        save(value); return value;
    }

    public synchronized Observation restored(String id) {
        var old = find(id);
        if (!Boolean.TRUE.equals(plugin(old.pluginId()).getSpec().getEnabled())) {
            throw new DiagnosticConflictException("尚未确认插件重新启用");
        }
        var value = old.finish("restored", old.after(), "插件已重新启用");
        save(value); return value;
    }

    public synchronized void tick(long now) {
        for (var value : list()) {
            if (!value.active()) { continue; }
            if ("prepared".equals(value.state())) {
                if (now - value.createdAtMs() > 120_000) { save(value.finish("cancelled", null, "未在两分钟内确认停用，已取消观察")); }
                continue;
            }
            if (Boolean.TRUE.equals(plugin(value.pluginId()).getSpec().getEnabled())) {
                save(value.finish("interrupted", null, "观察期间插件重新启用，对比已中断")); continue;
            }
            if (now >= value.toMs() + 30_000) {
                var after = metrics.summarize(value.fromMs(), value.toMs());
                String conclusion = !value.before().sufficient() || !after.sufficient()
                    ? "采样不足或不连续，暂不能判断优化效果"
                    : "对比已完成：请同时查看 CPU、堆、GC、P95 和请求量，降低不等于已证明由该插件导致";
                save(value.finish("completed", after, conclusion));
                events.record("optimization", "info", value.pluginId(), "插件优化观察已完成", conclusion);
            }
        }
    }

    public synchronized void interruptPending() {
        for (var value : list()) {
            if (value.active()) { save(value.finish("interrupted", null, "运行洞察或 Halo 重启，观察中断，可重新发起")); }
        }
    }

    public List<Observation> list() { return records.list(KIND, Observation.class, 100); }
    private Observation find(String id) {
        return list().stream().filter(value -> value.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("观察记录不存在"));
    }
    private Plugin plugin(String id) {
        var plugin = plugins.fetch(Plugin.class, id).block(Duration.ofSeconds(3));
        if (plugin == null || plugin.getSpec() == null) { throw new IllegalArgumentException("插件不存在"); }
        return plugin;
    }
    private void save(Observation value) { records.save(KIND, value.id(), System.currentTimeMillis(), value); }

    public record Observation(String id, String pluginId, String displayName, String state,
        long createdAtMs, long fromMs, long toMs, ObservationMetrics.Summary before,
        ObservationMetrics.Summary after, String message) {
        public boolean active() { return "prepared".equals(state) || "observing".equals(state); }
        Observation finish(String state, ObservationMetrics.Summary after, String message) {
            return new Observation(id, pluginId, displayName, state, createdAtMs, fromMs, toMs, before, after, message);
        }
    }
}
