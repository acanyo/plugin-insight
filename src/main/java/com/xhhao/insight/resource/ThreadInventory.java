package com.xhhao.insight.resource;

import java.util.List;

interface ThreadInventory {

    ThreadScan scan(int maximumThreads);

    record ThreadScan(List<Thread> threads, boolean truncated) {
    }
}
