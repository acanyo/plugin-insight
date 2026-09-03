package com.xhhao.insight.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Enumerates without collecting every stack trace and caps all subsequent MXBean work. */
final class BoundedThreadInventory implements ThreadInventory {

    @Override
    public ThreadScan scan(int maximumThreads) {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        Thread[] buffer = new Thread[Math.addExact(maximumThreads, 1)];
        int count = root.enumerate(buffer, true);
        boolean truncated = count > maximumThreads || count == buffer.length;
        List<Thread> threads = new ArrayList<>(Math.min(count, maximumThreads));
        for (int index = 0; index < Math.min(count, maximumThreads); index++) {
            Thread thread = buffer[index];
            if (thread != null && thread.isAlive()) {
                threads.add(thread);
            }
        }
        threads.sort(Comparator.comparingLong(Thread::threadId));
        return new ThreadScan(List.copyOf(threads), truncated);
    }
}
