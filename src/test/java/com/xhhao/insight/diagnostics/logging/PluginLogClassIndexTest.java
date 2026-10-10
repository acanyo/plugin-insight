package com.xhhao.insight.diagnostics.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.insight.halo.HaloRuntimeSnapshot;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.runtime.MonitoringState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class PluginLogClassIndexTest {
    @TempDir Path temporary;

    @Test
    void indexesJarMetadataAndStoppedPluginsWithoutLoadingClasses() throws Exception {
        var alpha =
                plugin(
                        "alpha",
                        jar(
                                "alpha.jar",
                                "com/example/alpha/Worker.class",
                                "META-INF/versions/21/com/example/Other.class",
                                "lib/Library.class",
                                "lib/dependency.jar"));
        var beta = plugin("beta", jar("beta.jar", "com/example/beta/Worker.class"));
        var manager = mock(PluginManager.class);
        when(manager.getPlugins()).thenReturn(List.of(alpha, beta));
        var state = new MonitoringState();
        state.updateHalo(
                new HaloRuntimeSnapshot(
                        "2.26.0",
                        null,
                        List.of(
                                new PluginRuntimeInfo("alpha", "甲插件", "1.0", null, true, "STARTED", null),
                                new PluginRuntimeInfo(
                                        "beta", "乙插件", "1.0", null, false, "STOPPED", null)),
                        List.of()));

        var snapshot = new PluginLogClassIndex(manager, state, () -> 0).snapshot();

        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.classOwners())
                .containsOnlyKeys("com.example.alpha.Worker", "com.example.beta.Worker");
        assertThat(snapshot.classOwners().get("com.example.beta.Worker")).containsExactly("beta");
        assertThat(snapshot.plugins())
                .contains(
                        new PluginLogClassIndex.PluginOption("alpha", "甲插件", true),
                        new PluginLogClassIndex.PluginOption("beta", "乙插件", true));
        verify(alpha, never()).getPluginClassLoader();
        verify(beta, never()).getPluginClassLoader();
        verify(manager, never()).getStartedPlugins();
    }

    @Test
    void indexesDevelopmentClassDirectoriesAndSharesTheCacheForOneMinute() throws Exception {
        Path project = temporary.resolve("project");
        Path javaClass = project.resolve("build/classes/java/main/com/example/alpha/Worker.class");
        Files.createDirectories(javaClass.getParent());
        Files.write(javaClass, new byte[] {0});
        var manager = mock(PluginManager.class);
        var alpha = plugin("alpha", project);
        when(manager.getPlugins()).thenReturn(List.of(alpha));
        var now = new AtomicLong();
        var index = new PluginLogClassIndex(manager, new MonitoringState(), now::get);

        var initial = index.snapshot();
        Files.delete(javaClass);
        assertThat(index.snapshot()).isSameAs(initial);
        verify(manager, times(1)).getPlugins();
        now.set(Duration.ofMinutes(1).toNanos());
        assertThat(index.snapshot().classOwners()).isEmpty();
        verify(manager, times(2)).getPlugins();
        assertThat(initial.classOwners()).containsKey("com.example.alpha.Worker");
    }

    @Test
    void preservesAmbiguousOwnersAndMarksUnavailablePlugins() throws Exception {
        var manager = mock(PluginManager.class);
        var plugins =
                List.of(
                        plugin("alpha", jar("alpha.jar", "com/example/shared/Worker.class")),
                        plugin("beta", jar("beta.jar", "com/example/shared/Worker.class")),
                        plugin("missing", temporary.resolve("removed.jar")),
                        plugin("frontend", jar("frontend.jar", "ui/main.js")));
        when(manager.getPlugins()).thenReturn(plugins);

        var snapshot = new PluginLogClassIndex(manager, new MonitoringState(), () -> 0).snapshot();
        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.classOwners().get("com.example.shared.Worker"))
                .containsExactlyInAnyOrder("alpha", "beta");
        assertThat(snapshot.plugins())
                .contains(
                        new PluginLogClassIndex.PluginOption("missing", "missing", false),
                        new PluginLogClassIndex.PluginOption("frontend", "frontend", false));
    }

    private PluginWrapper plugin(String id, Path path) {
        var plugin = mock(PluginWrapper.class);
        when(plugin.getPluginId()).thenReturn(id);
        when(plugin.getPluginPath()).thenReturn(path);
        return plugin;
    }

    private Path jar(String name, String... entries) throws Exception {
        Path path = temporary.resolve(name);
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                output.putNextEntry(new JarEntry(entry));
                output.write(0);
                output.closeEntry();
            }
        }
        return path;
    }
}
