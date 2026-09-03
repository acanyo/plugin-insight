package com.xhhao.insight.api;

import com.xhhao.insight.api.model.TrendResponse;
import com.xhhao.insight.storage.model.SystemMetricMinute;
import com.xhhao.insight.storage.model.SystemSample;
import com.xhhao.insight.storage.repository.SystemMetricMinuteRepository;
import com.xhhao.insight.storage.repository.SystemSampleRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Selects the appropriate system-metric resolution and bounds the chart payload. */
@Component
public class SystemTrendService {

    static final int MAX_POINTS = 10_000;
    private static final long RAW_RANGE_LIMIT_MS = Duration.ofHours(6).toMillis();
    private static final int MAX_RAW_SAMPLES = 10_000;

    private final SystemSampleRepository samples;
    private final SystemMetricMinuteRepository minutes;

    public SystemTrendService(
        SystemSampleRepository samples,
        SystemMetricMinuteRepository minutes
    ) {
        this.samples = samples;
        this.minutes = minutes;
    }

    public List<TrendResponse.SystemPoint> find(ApiTimeRange range) {
        if (range.toMs() - range.fromMs() <= RAW_RANGE_LIMIT_MS) {
            return rawPoints(samples.findRange(range.fromMs(), range.toMs(), MAX_RAW_SAMPLES));
        }
        return minutePoints(minutes.findRange(range.fromMs(), range.toMs()));
    }

    private static List<TrendResponse.SystemPoint> rawPoints(List<SystemSample> samples) {
        List<TrendResponse.SystemPoint> points = new ArrayList<>(samples.size());
        SystemSample previous = null;
        for (SystemSample sample : samples) {
            long gcPauseDelta = previous != null && sample.uptimeMs() >= previous.uptimeMs()
                ? nonNegativeDelta(sample.gcPauseMs(), previous.gcPauseMs()) : 0;
            points.add(new TrendResponse.SystemPoint(
                sample.sampledAtMs(), sample.processCpuLoad(), sample.systemCpuLoad(),
                sample.heapUsedBytes(), sample.heapMaxBytes(), sample.systemMemoryUsedBytes(),
                sample.systemMemoryTotalBytes(), sample.diskUsedBytes(), sample.diskTotalBytes(),
                sample.liveThreads(), gcPauseDelta
            ));
            previous = sample;
        }
        return points;
    }

    private static List<TrendResponse.SystemPoint> minutePoints(
        List<SystemMetricMinute> metrics
    ) {
        if (metrics.isEmpty()) {
            return List.of();
        }
        int step = Math.max(1, (metrics.size() + MAX_POINTS - 1) / MAX_POINTS);
        List<TrendResponse.SystemPoint> points = new ArrayList<>(
            (metrics.size() + step - 1) / step);
        for (int from = 0; from < metrics.size(); from += step) {
            int to = Math.min(metrics.size(), from + step);
            points.add(combine(metrics.subList(from, to)));
        }
        return points;
    }

    private static TrendResponse.SystemPoint combine(List<SystemMetricMinute> metrics) {
        long samples = 0;
        double processCpuWeighted = 0;
        double systemCpuWeighted = 0;
        double heapUsedWeighted = 0;
        double memoryUsedWeighted = 0;
        double liveThreadsWeighted = 0;
        long gcPauseMs = 0;
        for (SystemMetricMinute metric : metrics) {
            samples += metric.sampleCount();
            processCpuWeighted += metric.processCpuAverage() * metric.sampleCount();
            systemCpuWeighted += metric.systemCpuAverage() * metric.sampleCount();
            heapUsedWeighted += (double) metric.heapUsedAverageBytes() * metric.sampleCount();
            memoryUsedWeighted +=
                (double) metric.systemMemoryUsedAverageBytes() * metric.sampleCount();
            liveThreadsWeighted += metric.liveThreadsAverage() * metric.sampleCount();
            gcPauseMs = saturatingAdd(gcPauseMs, metric.gcPauseMsDelta());
        }
        SystemMetricMinute last = metrics.getLast();
        double divisor = Math.max(1, samples);
        return new TrendResponse.SystemPoint(
            last.lastSampledAtMs(), processCpuWeighted / divisor, systemCpuWeighted / divisor,
            Math.round(heapUsedWeighted / divisor), last.heapMaxLastBytes(),
            Math.round(memoryUsedWeighted / divisor), last.systemMemoryTotalLastBytes(),
            last.diskUsedLastBytes(), last.diskTotalLastBytes(),
            (int) Math.round(liveThreadsWeighted / divisor), gcPauseMs
        );
    }

    private static long nonNegativeDelta(long current, long previous) {
        return current >= previous ? current - previous : 0;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
