package com.xhhao.insight.event;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Watcher;

@Component
public class HaloPluginEventWatcher implements Watcher {

    private final ReactiveExtensionClient extensionClient;
    private final PluginTimelineRecorder timeline;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private volatile Runnable disposeHook;

    public HaloPluginEventWatcher(
        ReactiveExtensionClient extensionClient,
        PluginTimelineRecorder timeline
    ) {
        this.extensionClient = extensionClient;
        this.timeline = timeline;
    }

    public void start() {
        disposed.set(false);
        extensionClient.watch(this);
    }

    @Override
    public void onAdd(Extension extension) {
        if (extension instanceof Plugin plugin) {
            timeline.pluginAdded(plugin);
        }
    }

    @Override
    public void onUpdate(Extension oldExtension, Extension newExtension) {
        if (!(oldExtension instanceof Plugin oldPlugin)
            || !(newExtension instanceof Plugin newPlugin)) {
            return;
        }
        timeline.pluginUpdated(oldPlugin, newPlugin);
    }

    @Override
    public void onDelete(Extension extension) {
        if (extension instanceof Plugin plugin) {
            timeline.pluginDeleted(plugin);
        }
    }

    @Override
    public void registerDisposeHook(Runnable dispose) {
        this.disposeHook = dispose;
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true) && disposeHook != null) {
            disposeHook.run();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
