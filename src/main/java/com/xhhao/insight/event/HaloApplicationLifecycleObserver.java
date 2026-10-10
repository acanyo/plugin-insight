package com.xhhao.insight.event;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.SpringPluginManager;

/** Observes only signals emitted by Halo's root Spring context. */
@Slf4j
@Component
public final class HaloApplicationLifecycleObserver {

    private final ApplicationContext rootContext;
    private final EventRecorder events;
    private final ApplicationListener<ApplicationEvent> rootListener = this::onRootEvent;
    private final AtomicReference<ReadinessState> lastReadiness = new AtomicReference<>();
    private final AtomicBoolean closingRecorded = new AtomicBoolean();

    private volatile boolean active;
    private ApplicationEventMulticaster eventMulticaster;

    @Autowired
    public HaloApplicationLifecycleObserver(PluginWrapper currentPlugin, EventRecorder events) {
        this(rootContext(currentPlugin), events);
    }

    HaloApplicationLifecycleObserver(ApplicationContext rootContext, EventRecorder events) {
        this.rootContext = rootContext;
        this.events = events;
    }

    public synchronized void start() {
        if (active || rootContext == null) {
            return;
        }
        try {
            eventMulticaster = rootContext.getBean(
                AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                ApplicationEventMulticaster.class
            );
            lastReadiness.set(null);
            closingRecorded.set(false);
            active = true;
            eventMulticaster.addApplicationListener(rootListener);
            observeCurrentReadiness();
        } catch (RuntimeException error) {
            removeListener();
            log.warn("Unable to observe Halo root application lifecycle", error);
        }
    }

    public synchronized void stop() {
        removeListener();
    }

    private void removeListener() {
        active = false;
        if (eventMulticaster != null) {
            try {
                eventMulticaster.removeApplicationListener(rootListener);
            } catch (RuntimeException error) {
                log.debug("Unable to detach Halo root lifecycle listener during shutdown", error);
            }
            eventMulticaster = null;
        }
    }

    private void observeCurrentReadiness() {
        ApplicationAvailability availability = rootContext
            .getBeanProvider(ApplicationAvailability.class)
            .getIfAvailable();
        if (availability != null) {
            recordReadiness(availability.getReadinessState(), "startup_observation");
        }
    }

    private void onRootEvent(ApplicationEvent event) {
        if (!active) {
            return;
        }
        if (event instanceof ApplicationReadyEvent readyEvent
            && readyEvent.getApplicationContext() == rootContext) {
            recordReadiness(ReadinessState.ACCEPTING_TRAFFIC, "application_ready_event");
            return;
        }
        if (event instanceof AvailabilityChangeEvent<?> availabilityEvent
            && availabilityEvent.getSource() == rootContext
            && availabilityEvent.getState() instanceof ReadinessState readinessState) {
            recordReadiness(readinessState, "readiness_event");
            return;
        }
        if (event instanceof ContextClosedEvent closedEvent
            && closedEvent.getApplicationContext() == rootContext
            && closingRecorded.compareAndSet(false, true)) {
            events.record("lifecycle", "info", "halo", "Halo 应用上下文正在关闭",
                "检测到 Halo 根 Spring 上下文关闭；这与单独停用运行洞察不同",
                EventDetailsEncoder.encode(new HaloLifecycleDetails(
                    1, "halo_application_lifecycle", "context_closed_event", null)));
        }
    }

    private void recordReadiness(ReadinessState readiness, String signal) {
        if (readiness == null || lastReadiness.getAndSet(readiness) == readiness) {
            return;
        }
        boolean observedAtStartup = "startup_observation".equals(signal);
        String title;
        String summary;
        String severity;
        if (readiness == ReadinessState.ACCEPTING_TRAFFIC) {
            title = observedAtStartup ? "Halo 应用就绪状态已确认" : "Halo 应用已接受流量";
            summary = observedAtStartup
                ? "运行洞察启动时确认 Halo 已处于可接收请求状态"
                : "Halo 根 Spring 上下文报告可以接收请求";
            severity = "info";
        } else {
            title = "Halo 应用暂不接受流量";
            summary = observedAtStartup
                ? "运行洞察启动时观察到 Halo 尚未进入可接收请求状态"
                : "Halo 根 Spring 上下文报告暂不接收请求";
            severity = "warning";
        }
        events.record("lifecycle", severity, "halo", title, summary,
            EventDetailsEncoder.encode(new HaloLifecycleDetails(
                1, "halo_application_readiness", signal, readiness.name())));
    }

    private static ApplicationContext rootContext(PluginWrapper currentPlugin) {
        return currentPlugin.getPluginManager() instanceof SpringPluginManager manager
            ? manager.getRootContext()
            : null;
    }

    private record HaloLifecycleDetails(
        int schemaVersion,
        String kind,
        String signal,
        String readiness
    ) {}
}
