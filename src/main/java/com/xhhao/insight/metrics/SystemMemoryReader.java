package com.xhhao.insight.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Reads host memory using the same available-memory semantics as Linux free(1). */
final class SystemMemoryReader {
    private static final Path LINUX_MEMINFO = Path.of("/proc/meminfo");
    private static final long KIB = 1_024L;

    private SystemMemoryReader() {}

    static MemorySnapshot read(com.sun.management.OperatingSystemMXBean operatingSystem) {
        MemorySnapshot linux = readLinuxMeminfo();
        if (linux != null) {
            return linux;
        }
        if (operatingSystem == null) {
            return new MemorySnapshot(0, 0);
        }
        long total = nonNegative(operatingSystem.getTotalMemorySize());
        long free = nonNegative(operatingSystem.getFreeMemorySize());
        return new MemorySnapshot(total, Math.max(0, total - free));
    }

    private static MemorySnapshot readLinuxMeminfo() {
        if (!Files.isReadable(LINUX_MEMINFO)) {
            return null;
        }
        try {
            Map<String, Long> values = new HashMap<>();
            for (String line : Files.readAllLines(LINUX_MEMINFO)) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String[] fields = line.substring(colon + 1).trim().split("\\s+");
                if (fields.length == 0) {
                    continue;
                }
                long value = Long.parseLong(fields[0]);
                String unit = fields.length > 1 ? fields[1] : "";
                values.put(key, "kB".equalsIgnoreCase(unit) ? value * KIB : value);
            }
            long total = nonNegative(values.getOrDefault("MemTotal", 0L));
            long available = nonNegative(values.getOrDefault("MemAvailable", 0L));
            if (total <= 0 || available <= 0) {
                return null;
            }
            return new MemorySnapshot(total, Math.max(0, total - Math.min(total, available)));
        } catch (IOException | NumberFormatException | SecurityException ignored) {
            return null;
        }
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    record MemorySnapshot(long totalBytes, long usedBytes) {}
}
