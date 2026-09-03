package com.xhhao.insight.resource;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

/** Enables and reads optional JVM thread counters without requiring JFR or an agent. */
final class JmxThreadResourceReader implements ThreadResourceReader {

    private final ThreadMXBean threadBean;

    JmxThreadResourceReader() {
        this(ManagementFactory.getThreadMXBean());
    }

    JmxThreadResourceReader(ThreadMXBean threadBean) {
        this.threadBean = threadBean;
    }

    @Override
    public ThreadResourceSnapshot read(long[] threadIds) {
        long[] cpu = unavailable(threadIds.length);
        long[] allocated = unavailable(threadIds.length);
        boolean cpuAvailable = enableCpuTime();
        if (cpuAvailable) {
            try {
                if (threadBean instanceof com.sun.management.ThreadMXBean extended) {
                    cpu = extended.getThreadCpuTime(threadIds);
                } else {
                    for (int index = 0; index < threadIds.length; index++) {
                        cpu[index] = threadBean.getThreadCpuTime(threadIds[index]);
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                cpuAvailable = false;
                cpu = unavailable(threadIds.length);
            }
        }
        boolean allocationAvailable = false;
        if (threadBean instanceof com.sun.management.ThreadMXBean extended
            && enableAllocatedMemory(extended)) {
            try {
                allocated = extended.getThreadAllocatedBytes(threadIds);
                allocationAvailable = true;
            } catch (RuntimeException | LinkageError failure) {
                allocated = unavailable(threadIds.length);
            }
        }
        return new ThreadResourceSnapshot(cpuAvailable, allocationAvailable, cpu, allocated);
    }

    private boolean enableCpuTime() {
        try {
            if (!threadBean.isThreadCpuTimeSupported()) {
                return false;
            }
            if (!threadBean.isThreadCpuTimeEnabled()) {
                threadBean.setThreadCpuTimeEnabled(true);
            }
            return threadBean.isThreadCpuTimeEnabled();
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static boolean enableAllocatedMemory(com.sun.management.ThreadMXBean bean) {
        try {
            if (!bean.isThreadAllocatedMemorySupported()) {
                return false;
            }
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return bean.isThreadAllocatedMemoryEnabled();
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static long[] unavailable(int length) {
        long[] values = new long[length];
        Arrays.fill(values, -1);
        return values;
    }
}
