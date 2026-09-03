package com.xhhao.insight.collection;

import java.util.concurrent.ScheduledExecutorService;

/** Creates one collector scheduler for each active plugin lifecycle. */
@FunctionalInterface
public interface MonitoringSchedulerFactory {

    ScheduledExecutorService create();
}
