package com.xhhao.insight.collection;

import com.xhhao.insight.alert.AlertEvaluationInput;
import com.xhhao.insight.metrics.RequestWindowSnapshot;
import com.xhhao.insight.metrics.SystemSnapshot;
import java.util.Objects;

/** Assembles alert-domain input from the current metrics snapshots. */
final class AlertEvaluationInputMapper {

    private AlertEvaluationInputMapper() {}

    static AlertEvaluationInput from(
        SystemSnapshot system,
        RequestWindowSnapshot requests
    ) {
        Objects.requireNonNull(system, "system must not be null");
        Objects.requireNonNull(requests, "requests must not be null");
        return new AlertEvaluationInput(
            system.systemCpuPercent(),
            system.heapUsedPercent(),
            system.diskUsedPercent(),
            requests.errorRatePercent(),
            requests.p95Ms(),
            system.deadlockedThreadCount()
        );
    }
}
