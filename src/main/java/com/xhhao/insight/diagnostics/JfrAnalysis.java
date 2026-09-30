package com.xhhao.insight.diagnostics;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import jdk.jfr.consumer.RecordingFile;

/** Bounded stack aggregation of CPU and allocation samples, not retained-heap attribution. */
public final class JfrAnalysis {
    private JfrAnalysis() {}

    public static Result read(Path path) throws IOException {
        var counts = new HashMap<String, Long>();
        var stacks = new HashMap<String, Long>();
        int read = 0;
        long deadline = System.nanoTime() + 5_000_000_000L;
        boolean truncated;
        try (var file = new RecordingFile(path)) {
            while (file.hasMoreEvents() && read < 200_000 && System.nanoTime() < deadline) {
                var event = file.readEvent(); read++;
                String type = event.getEventType().getName();
                counts.merge(type, 1L, Long::sum);
                if (("jdk.ExecutionSample".equals(type) || "jdk.ObjectAllocationSample".equals(type))
                    && event.getStackTrace() != null && !event.getStackTrace().getFrames().isEmpty()) {
                    String stack = event.getStackTrace().getFrames().stream().limit(12).map(frame ->
                        frame.getMethod().getType().getName() + "." + frame.getMethod().getName() + ":" + frame.getLineNumber())
                        .collect(java.util.stream.Collectors.joining("\n"));
                    String key = type + "\n" + stack;
                    if (stacks.size() < 500 || stacks.containsKey(key)) { stacks.merge(key, 1L, Long::sum); }
                }
            }
            truncated = file.hasMoreEvents();
        }
        return new Result(read, truncated, counts, stacks.entrySet().stream()
            .sorted(java.util.Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).limit(20)
            .map(entry -> new Hotspot(entry.getKey(), entry.getValue())).toList());
    }

    public record Hotspot(String stack, long samples) {}
    public record Result(int events, boolean truncated, java.util.Map<String, Long> counts, List<Hotspot> hotspots) {}
}
