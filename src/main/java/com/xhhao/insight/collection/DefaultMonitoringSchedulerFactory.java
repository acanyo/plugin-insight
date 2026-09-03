package com.xhhao.insight.collection;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.stereotype.Component;

@Component
final class DefaultMonitoringSchedulerFactory implements MonitoringSchedulerFactory {

    @Override
    public ScheduledExecutorService create() {
        return Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("insight-collector").daemon(true).factory());
    }
}
