package com.xhhao.insight.resource;

interface ThreadResourceReader {

    ThreadResourceSnapshot read(long[] threadIds);

    record ThreadResourceSnapshot(
        boolean cpuAvailable,
        boolean allocationAvailable,
        long[] cpuTimeNs,
        long[] allocatedBytes
    ) {
    }
}
