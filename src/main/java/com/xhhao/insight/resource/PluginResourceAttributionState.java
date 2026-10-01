package com.xhhao.insight.resource;

import org.springframework.stereotype.Component;

/** Lock-free current capability snapshot consumed by ranking queries. */
@Component
public class PluginResourceAttributionState {

    private volatile ResourceAttributionStatus status = ResourceAttributionStatus.warming();
    private volatile ResourceAttributionSample latestSample;

    public ResourceAttributionStatus snapshot() {
        return status;
    }

    public ResourceAttributionSample latestSample() {
        return latestSample;
    }

    void warming(boolean cpuAvailable, boolean allocationAvailable, long sampledAtMs) {
        latestSample = null;
        status = new ResourceAttributionStatus(
            "WARMING_UP", "NONE", cpuAvailable, allocationAvailable, false,
            sampledAtMs, ResourceAttributionStatus.METHOD);
    }

    void available(ResourceAttributionSample sample) {
        latestSample = sample;
        String availability = sample.cpuAvailable() && sample.allocationAvailable()
            ? "AVAILABLE" : "PARTIAL";
        boolean lowCoverage = percentage(
            sample.attributedCpuTimeNs(), sample.totalCpuTimeNs()) < 25
            && percentage(sample.attributedAllocatedBytes(), sample.totalAllocatedBytes()) < 25;
        status = new ResourceAttributionStatus(
            availability, sample.truncated() || lowCoverage ? "LOW" : "MEDIUM",
            sample.cpuAvailable(), sample.allocationAvailable(), sample.truncated(),
            sample.sampledAtMs(), ResourceAttributionStatus.METHOD);
    }

    void disabled(long sampledAtMs) {
        latestSample = null;
        status = new ResourceAttributionStatus(
            "DISABLED", "NONE", false, false, false, sampledAtMs,
            ResourceAttributionStatus.METHOD);
    }

    void unsupported(long sampledAtMs) {
        latestSample = null;
        status = new ResourceAttributionStatus(
            "UNSUPPORTED", "NONE", false, false, false, sampledAtMs,
            ResourceAttributionStatus.METHOD);
    }

    boolean failed(long sampledAtMs) {
        latestSample = null;
        boolean transition = !"ERROR".equals(status.availability());
        status = new ResourceAttributionStatus(
            "ERROR", "NONE", false, false, false, sampledAtMs,
            ResourceAttributionStatus.METHOD);
        return transition;
    }

    private static double percentage(long attributed, long total) {
        return total == 0 ? 0 : (double) attributed / total * 100;
    }
}
