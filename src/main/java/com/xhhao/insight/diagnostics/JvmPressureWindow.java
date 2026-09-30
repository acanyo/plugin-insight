package com.xhhao.insight.diagnostics;

import com.xhhao.insight.metrics.SystemSnapshot;
import java.util.ArrayDeque;
import java.util.List;
import org.springframework.stereotype.Component;

/** Counter deltas only within one JVM lifetime; missing coverage stays visible. */
@Component
public class JvmPressureWindow {
    private final ArrayDeque<SystemSnapshot> samples = new ArrayDeque<>();

    public synchronized void add(SystemSnapshot sample) {
        if (sample == null) { return; }
        var last = samples.peekLast();
        if (last != null && sample.timestampMs() == last.timestampMs()) { return; }
        if (last != null && (sample.uptimeMs() < last.uptimeMs()
            || sample.gcCount() < last.gcCount() || sample.timestampMs() < last.timestampMs())) {
            samples.clear();
        }
        samples.addLast(sample);
        while (samples.size() > 1000 || samples.peekFirst().timestampMs() < sample.timestampMs() - 3_600_000) {
            samples.removeFirst();
        }
    }

    public synchronized List<GcWindow> windows(long now) {
        return List.of(window(1, now), window(5, now), window(15, now));
    }

    private GcWindow window(int minutes, long now) {
        var values = samples.stream().filter(s -> s.timestampMs() >= now - minutes * 60_000L).toList();
        if (values.size() < 2) { return new GcWindow(minutes, 0, 0, 0, 0, false); }
        var first = values.getFirst(); var last = values.getLast();
        long elapsed = last.uptimeMs() - first.uptimeMs();
        long time = Math.max(0, last.gcTimeMs() - first.gcTimeMs());
        boolean fresh = now - last.timestampMs() < 60_000;
        boolean continuous = true;
        for (int i = 1; i < values.size(); i++) {
            continuous &= values.get(i).timestampMs() - values.get(i - 1).timestampMs() <= 90_000;
        }
        return new GcWindow(minutes, elapsed, last.gcCount() - first.gcCount(), time,
            elapsed > 0 ? Math.min(100, 100d * time / elapsed) : 0,
            fresh && continuous && elapsed >= minutes * 60_000L * .75);
    }

    public record GcWindow(int minutes, long observedMs, long count, long timeMs,
                           double overheadPercent, boolean sufficient) {}
}
