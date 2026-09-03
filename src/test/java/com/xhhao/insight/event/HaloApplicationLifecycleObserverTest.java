package com.xhhao.insight.event;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.AbstractApplicationContext;

class HaloApplicationLifecycleObserverTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void observesOnlyRootContextReadinessAndShutdownSignals() {
        ApplicationContext root = mock(ApplicationContext.class);
        ApplicationContext other = mock(ApplicationContext.class);
        ApplicationEventMulticaster multicaster = mock(ApplicationEventMulticaster.class);
        ApplicationAvailability availability = mock(ApplicationAvailability.class);
        ObjectProvider<ApplicationAvailability> provider = mock(ObjectProvider.class);
        EventRecorder events = mock(EventRecorder.class);
        when(root.getBean(
            AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
            ApplicationEventMulticaster.class
        )).thenReturn(multicaster);
        when(root.getBeanProvider(ApplicationAvailability.class)).thenReturn(provider);
        when(provider.getIfAvailable()).thenReturn(availability);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);

        HaloApplicationLifecycleObserver observer =
            new HaloApplicationLifecycleObserver(root, events);
        observer.start();

        verify(events).record(eq("lifecycle"), eq("info"), eq("halo"),
            eq("Halo 应用就绪状态已确认"), contains("Insight 启动时确认"),
            contains("\"signal\":\"startup_observation\""));

        ArgumentCaptor<ApplicationListener> captor =
            ArgumentCaptor.forClass(ApplicationListener.class);
        verify(multicaster).addApplicationListener(captor.capture());
        ApplicationListener<ApplicationEvent> listener = captor.getValue();
        reset(events);

        listener.onApplicationEvent(
            new AvailabilityChangeEvent<>(other, ReadinessState.REFUSING_TRAFFIC));
        listener.onApplicationEvent(new ContextClosedEvent(other));
        verify(events, never()).record(
            eq("lifecycle"), eq("info"), eq("halo"),
            eq("Halo 应用上下文正在关闭"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());

        listener.onApplicationEvent(
            new AvailabilityChangeEvent<>(root, ReadinessState.REFUSING_TRAFFIC));
        verify(events).record(eq("lifecycle"), eq("warning"), eq("halo"),
            eq("Halo 应用暂不接受流量"), contains("暂不接收请求"),
            contains("\"readiness\":\"REFUSING_TRAFFIC\""));

        listener.onApplicationEvent(new ContextClosedEvent(root));
        listener.onApplicationEvent(new ContextClosedEvent(root));
        verify(events).record(eq("lifecycle"), eq("info"), eq("halo"),
            eq("Halo 应用上下文正在关闭"), contains("根 Spring 上下文关闭"),
            contains("\"signal\":\"context_closed_event\""));

        observer.stop();
        verify(multicaster).removeApplicationListener(captor.getValue());
    }
}
