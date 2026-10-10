package com.xhhao.insight.api;

import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.repository.PluginDiagnosticRepository;
import com.xhhao.insight.storage.repository.PluginDiagnosticRepository.Point;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Read-only comparisons of completed windows, with explicit sampling and traffic limits. */
@Component
public class PluginBaselineQueryService {
    private final PluginDiagnosticQueryService details;
    private final PluginDiagnosticRepository repository;
    private final SqliteStorageLifecycle storage;
    private final Clock clock;

    @Autowired
    public PluginBaselineQueryService(
            PluginDiagnosticQueryService details,
            PluginDiagnosticRepository repository,
            SqliteStorageLifecycle storage) {
        this(details, repository, storage, Clock.systemUTC());
    }

    PluginBaselineQueryService(
            PluginDiagnosticQueryService details,
            PluginDiagnosticRepository repository,
            SqliteStorageLifecycle storage,
            Clock clock) {
        this.details = details;
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    public Comparison compare(String pluginId, String rangeName, String reference) {
        details.plugin(pluginId);
        if (!List.of("previous", "yesterday", "last-week", "upgrade").contains(reference))
            throw new IllegalArgumentException("对比方式无效");
        long end = clock.millis() / 60_000 * 60_000;
        var range = ApiTimeRange.parse(rangeName, end);
        long duration = end - range.fromMs();
        if (!storage.health().available()) return unavailable(reference, "历史存储不可用。");
        long afterFrom = end - duration, afterTo = end, beforeFrom, beforeTo;
        if ("upgrade".equals(reference)) {
            long upgrade =
                    repository.latestUpgrade(pluginId, Math.max(0, end - 365 * 86_400_000L), end);
            if (upgrade == 0) return unavailable(reference, "没有可用的插件版本变更记录，无法确定对比边界。");
            long boundary = upgrade / 60_000 * 60_000;
            beforeFrom = boundary - duration;
            beforeTo = boundary;
            afterFrom = boundary + 60_000;
            afterTo = afterFrom + duration;
            if (afterTo > end) return unavailable(reference, "升级后尚未积累完整的所选窗口，请缩短范围或稍后查询。");
        } else {
            long offset =
                    switch (reference) {
                        case "yesterday" -> 86_400_000L;
                        case "last-week" -> 7 * 86_400_000L;
                        default -> duration;
                    };
            if (duration > offset) return unavailable(reference, "对比窗口不能重叠，请缩短时间范围。");
            beforeFrom = afterFrom - offset;
            beforeTo = afterTo - offset;
        }
        if (beforeFrom < 0) return unavailable(reference, "对比窗口超出可用时间范围。");
        var before =
                summarize(
                        repository.timeline(pluginId, beforeFrom, beforeTo), beforeFrom, beforeTo);
        var after =
                summarize(repository.timeline(pluginId, afterFrom, afterTo), afterFrom, afterTo);
        var notices = new ArrayList<String>();
        if (!before.sufficient() || !after.sufficient())
            notices.add("至少一侧资源覆盖不足 75% 或不足 3 分钟，仅展示已有记录");
        if (before.requests() == 0 || after.requests() == 0) notices.add("至少一侧没有插件请求，延迟与错误率不可直接比较");
        else if (difference(before.requests(), after.requests()) > 0.25)
            notices.add("前后请求量差异超过 25%，流量变化可能影响结果");
        double firstRate =
                before.observedMinutes() == 0
                        ? 0
                        : before.samples() / (double) before.observedMinutes();
        double secondRate =
                after.observedMinutes() == 0
                        ? 0
                        : after.samples() / (double) after.observedMinutes();
        boolean comparable =
                firstRate > 0 && secondRate > 0 && difference(firstRate, secondRate) <= 0.25;
        if (!comparable) notices.add("采样密度存在差异或缺失，请结合覆盖范围判断");
        if (before.cpuObservedMinutes() < before.observedMinutes()
                || after.cpuObservedMinutes() < after.observedMinutes()
                || before.allocationObservedMinutes() < before.observedMinutes()
                || after.allocationObservedMinutes() < after.observedMinutes()) {
            notices.add("部分资源分钟缺少 CPU 或分配量计数；相应速率仅按有计数的分钟平均");
        }
        notices.add("CPU 与分配量只覆盖归属线程；比较不证明变化由该插件升级引起");
        return new Comparison(true, reference, before, after, comparable, List.copyOf(notices));
    }

    static Summary summarize(List<Point> points, long from, long to) {
        int observed = points.stream().mapToInt(Point::resourceMinutes).sum();
        long samples = points.stream().mapToLong(Point::samples).sum();
        long cpu = points.stream().mapToLong(Point::cpuSamples).sum();
        long allocation = points.stream().mapToLong(Point::allocationSamples).sum();
        int cpuMinutes = points.stream().mapToInt(Point::cpuObservedMinutes).sum();
        int allocationMinutes = points.stream().mapToInt(Point::allocationObservedMinutes).sum();
        long requests = points.stream().mapToLong(Point::requests).sum();
        long errors = points.stream().mapToLong(Point::errors).sum();
        long expected = Math.max(1, (to - from) / 60_000);
        return new Summary(
                from,
                to,
                observed,
                expected,
                Math.min(100, observed * 100d / expected),
                samples,
                observed >= 3 && observed >= expected * 0.75,
                cpuMinutes == 0
                        ? null
                        : points.stream().mapToDouble(Point::cpuTimeMs).sum()
                                * 100
                                / (cpuMinutes * 60_000d),
                allocationMinutes == 0
                        ? null
                        : points.stream().mapToLong(Point::allocatedBytes).sum()
                                / (double) allocationMinutes,
                requests,
                requests == 0 ? null : errors * 100d / requests,
                requests == 0
                        ? null
                        : points.stream().mapToDouble(Point::requestDurationMs).sum() / requests,
                points.stream().mapToInt(Point::maximumThreads).max().orElse(0),
                cpu,
                allocation,
                cpuMinutes,
                allocationMinutes);
    }

    private static double difference(double before, double after) {
        return before == 0 ? after == 0 ? 0 : 1 : Math.abs(after - before) / before;
    }

    private static Comparison unavailable(String reference, String message) {
        return new Comparison(false, reference, null, null, false, List.of(message));
    }

    public record Comparison(
            boolean available,
            String reference,
            Summary before,
            Summary after,
            boolean samplingComparable,
            List<String> notices) {}

    public record Summary(
            long fromMs,
            long toMs,
            int observedMinutes,
            long expectedMinutes,
            double coveragePercent,
            long samples,
            boolean sufficient,
            Double cpuCorePercent,
            Double allocatedBytesPerMinute,
            long requests,
            Double errorRatePercent,
            Double averageRequestMs,
            int maximumThreads,
            long cpuSamples,
            long allocationSamples,
            int cpuObservedMinutes,
            int allocationObservedMinutes) {}
}
