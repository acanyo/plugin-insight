package com.xhhao.insight.resource;

public record ResourceAttributionStatus(
    String availability,
    String confidence,
    boolean cpuAvailable,
    boolean allocationAvailable,
    boolean truncated,
    long sampledAtMs,
    String attributionMethod
) {

    static final String METHOD = "thread-context-classloader";

    static ResourceAttributionStatus warming() {
        return new ResourceAttributionStatus(
            "WARMING_UP", "NONE", false, false, false, 0, METHOD);
    }
}
