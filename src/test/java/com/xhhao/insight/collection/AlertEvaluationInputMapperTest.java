package com.xhhao.insight.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.xhhao.insight.metrics.RequestWindowSnapshot;
import com.xhhao.insight.metrics.SystemSnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertEvaluationInputMapperTest {

    @Test
    void mapsSystemAndRequestSnapshotsIntoAlertDomainValues() {
        SystemSnapshot system = new SystemSnapshot(
            1_000, 500, 12, 80, 1,
            75, 100, 20, 400, 1_000,
            2, 3, 10, 5, 12, 2,
            1_000, 250
        );
        RequestWindowSnapshot requests = new RequestWindowSnapshot(
            0, 1_000, 20, 4, 2_000, 500, 450, 500, Map.of(2, 16L, 5, 4L)
        );

        var input = AlertEvaluationInputMapper.from(system, requests);

        assertThat(input.systemCpuPercent()).isEqualTo(80);
        assertThat(input.heapUsedPercent()).isEqualTo(75);
        assertThat(input.diskUsedPercent()).isEqualTo(75);
        assertThat(input.requestErrorRatePercent()).isEqualTo(20);
        assertThat(input.requestP95Millis()).isEqualTo(450);
        assertThat(input.deadlockedThreadCount()).isEqualTo(2);
    }
}
