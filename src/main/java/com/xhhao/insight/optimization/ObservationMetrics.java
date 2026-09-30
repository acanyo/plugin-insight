package com.xhhao.insight.optimization;

import com.xhhao.insight.metrics.LatencyBuckets;
import com.xhhao.insight.storage.model.SystemSample;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import com.xhhao.insight.storage.repository.SystemSampleRepository;
import org.springframework.stereotype.Component;

/** Uses equal, complete minute windows to avoid mixing pre/post intervention requests. */
@Component
public class ObservationMetrics {
    private final SystemSampleRepository systems;
    private final RequestMetricRepository requests;

    public ObservationMetrics(SystemSampleRepository systems, RequestMetricRepository requests) {
        this.systems = systems; this.requests = requests;
    }

    public Summary summarize(long from, long to) {
        var samples = systems.findRange(from, to, 200);
        var minutes = requests.findTrendRange(from, to);
        long[] buckets = new long[11];
        long count = 0, maximumMs = 0;
        for (var minute : minutes) {
            count += minute.requestCount(); maximumMs = Math.max(maximumMs, minute.durationMaxUs() / 1000);
            var h = minute.histogram();
            long[] values = {h.le5Ms(), h.le10Ms(), h.le25Ms(), h.le50Ms(), h.le100Ms(), h.le250Ms(),
                h.le500Ms(), h.le1000Ms(), h.le2500Ms(), h.le5000Ms(), h.gt5000Ms()};
            for (int i = 0; i < values.length; i++) { buckets[i] += values[i]; }
        }
        long elapsed = samples.size() < 2 ? 0 : samples.getLast().uptimeMs() - samples.getFirst().uptimeMs();
        long gc = samples.size() < 2 ? 0 : samples.getLast().gcPauseMs() - samples.getFirst().gcPauseMs();
        boolean continuous = elapsed > 0 && gc >= 0;
        for (int i = 1; i < samples.size(); i++) {
            continuous &= samples.get(i).uptimeMs() > samples.get(i - 1).uptimeMs()
                && samples.get(i).sampledAtMs() - samples.get(i - 1).sampledAtMs() <= 90_000;
        }
        return new Summary(from, to, samples.size(), continuous && elapsed >= (to - from) * .75,
            samples.stream().mapToDouble(SystemSample::processCpuLoad).average().orElse(0),
            samples.stream().mapToLong(SystemSample::heapUsedBytes).average().orElse(0),
            continuous ? Math.min(100, gc * 100d / elapsed) : 0,
            count, count == 0 ? null : LatencyBuckets.percentile(buckets, .95, maximumMs));
    }

    public record Summary(long fromMs, long toMs, int samples, boolean sufficient,
                          double cpuPercent, double heapBytes, double gcPercent,
                          long requests, Long p95Ms) {}
}
