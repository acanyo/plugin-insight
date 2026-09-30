package com.xhhao.insight.metrics;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginsRootGetter;

@Component
public class JvmSystemMetricsCollector {

    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> garbageCollectors =
        ManagementFactory.getGarbageCollectorMXBeans();
    private final com.sun.management.OperatingSystemMXBean operatingSystem;
    private final Path diskPath;
    private final Clock clock;

    @Autowired
    public JvmSystemMetricsCollector(PluginsRootGetter pluginsRootGetter) {
        this(pluginsRootGetter.get(), Clock.systemUTC());
    }

    JvmSystemMetricsCollector(Path diskPath, Clock clock) {
        this.diskPath = diskPath;
        this.clock = clock;
        var bean = ManagementFactory.getOperatingSystemMXBean();
        this.operatingSystem = bean instanceof com.sun.management.OperatingSystemMXBean os
            ? os : null;
    }

    public SystemSnapshot collect() {
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        long[] deadlocked = threads.findDeadlockedThreads();
        DiskUsage disk = diskUsage();
        SystemMemoryReader.MemorySnapshot systemMemory = SystemMemoryReader.read(operatingSystem);
        return new SystemSnapshot(
            clock.millis(),
            ManagementFactory.getRuntimeMXBean().getUptime(),
            cpuPercent(operatingSystem == null ? -1 : operatingSystem.getProcessCpuLoad()),
            cpuPercent(operatingSystem == null ? -1 : operatingSystem.getCpuLoad()),
            ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(),
            nonNegative(heap.getUsed()),
            positiveLimit(heap),
            nonNegative(nonHeap.getUsed()),
            systemMemory.usedBytes(),
            systemMemory.totalBytes(),
            garbageCollectors.stream().mapToLong(JvmSystemMetricsCollector::collectionCount).sum(),
            garbageCollectors.stream().mapToLong(JvmSystemMetricsCollector::collectionTime).sum(),
            threads.getThreadCount(),
            threads.getDaemonThreadCount(),
            threads.getPeakThreadCount(),
            deadlocked == null ? 0 : deadlocked.length,
            disk.totalBytes(),
            disk.freeBytes()
        );
    }

    private DiskUsage diskUsage() {
        try {
            Files.createDirectories(diskPath);
            FileStore store = Files.getFileStore(diskPath);
            return new DiskUsage(store.getTotalSpace(), store.getUsableSpace());
        } catch (IOException ignored) {
            return new DiskUsage(0, 0);
        }
    }

    private static long positiveLimit(MemoryUsage usage) {
        return usage.getMax() > 0 ? usage.getMax() : Math.max(usage.getCommitted(), usage.getUsed());
    }

    private static double cpuPercent(double load) {
        return load < 0 || Double.isNaN(load) ? 0 : Math.min(100, load * 100);
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }

    private static long collectionCount(GarbageCollectorMXBean bean) {
        return Math.max(0, bean.getCollectionCount());
    }

    private static long collectionTime(GarbageCollectorMXBean bean) {
        return Math.max(0, bean.getCollectionTime());
    }

    private record DiskUsage(long totalBytes, long freeBytes) {}
}
