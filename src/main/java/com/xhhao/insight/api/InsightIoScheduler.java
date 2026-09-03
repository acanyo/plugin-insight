package com.xhhao.insight.api;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
public class InsightIoScheduler implements DisposableBean {

    private final Scheduler scheduler = Schedulers.newBoundedElastic(
        4, 500, "insight-storage-io");

    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public void destroy() {
        scheduler.dispose();
    }
}
