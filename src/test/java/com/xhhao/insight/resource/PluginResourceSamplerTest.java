package com.xhhao.insight.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginResourceSamplerTest {

    @Test
    void emitsOnlyStableNonNegativeCounterDeltas() {
        ClassLoader loader = new ClassLoader() { };
        Thread pluginThread = new Thread(() -> { });
        pluginThread.setContextClassLoader(loader);
        ThreadInventory inventory = maximum ->
            new ThreadInventory.ThreadScan(List.of(pluginThread), false);
        SequenceReader reader = new SequenceReader(
            new long[][] {{100, 1_000}, {160, 1_300}});
        var state = new PluginResourceAttributionState();
        PluginResourceSampler sampler = samplerWithOwner(inventory, reader, state, loader);

        assertThat(sampler.sample(1_000, true, 32)).isEmpty();
        assertThat(sampler.sample(2_000, true, 32)).get()
            .satisfies(sample -> {
                assertThat(sample.plugins()).singleElement().satisfies(delta -> {
                    assertThat(delta.pluginId()).isEqualTo("plugin-a");
                    assertThat(delta.cpuTimeNs()).isEqualTo(60);
                    assertThat(delta.allocatedBytes()).isEqualTo(300);
                    assertThat(delta.cpuActiveSamples()).isEqualTo(1);
                });
                assertThat(sample.totalCpuTimeNs()).isEqualTo(60);
                assertThat(sample.attributedCpuTimeNs()).isEqualTo(60);
            });
        assertThat(state.snapshot().availability()).isEqualTo("AVAILABLE");
        assertThat(state.snapshot().confidence()).isEqualTo("MEDIUM");
    }

    @Test
    void disableClearsBaselineAndReportsCapabilityInsteadOfZeroUsage() {
        var state = new PluginResourceAttributionState();
        ThreadInventory inventory = maximum ->
            new ThreadInventory.ThreadScan(List.of(Thread.currentThread()), false);
        ThreadResourceReader reader = ids -> new ThreadResourceReader.ThreadResourceSnapshot(
            false, false, new long[] {-1}, new long[] {-1});
        PluginResourceSampler sampler = new PluginResourceSampler(
            inventory, reader, mock(PluginClassLoaderIndex.class), state);

        assertThat(sampler.sample(1_000, false, 32)).isEmpty();
        assertThat(state.snapshot().availability()).isEqualTo("DISABLED");
        assertThat(sampler.sample(2_000, true, 32)).isEmpty();
        assertThat(state.snapshot().availability()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void lifecycleRestartDiscardsThePreviousCounterBaseline() {
        ClassLoader loader = new ClassLoader() { };
        Thread pluginThread = new Thread(() -> { });
        pluginThread.setContextClassLoader(loader);
        ThreadInventory inventory = maximum ->
            new ThreadInventory.ThreadScan(List.of(pluginThread), false);
        SequenceReader reader = new SequenceReader(
            new long[][] {{100, 1_000}, {160, 1_300}, {9_000, 90_000}, {9_010, 90_020}});
        var state = new PluginResourceAttributionState();
        PluginResourceSampler sampler = samplerWithOwner(inventory, reader, state, loader);

        assertThat(sampler.sample(1_000, true, 32)).isEmpty();
        assertThat(sampler.sample(2_000, true, 32)).isPresent();
        sampler.stop(3_000);
        sampler.start(4_000);
        assertThat(sampler.sample(5_000, true, 32)).isEmpty();
        assertThat(sampler.sample(6_000, true, 32)).get()
            .satisfies(sample -> assertThat(sample.plugins().getFirst().cpuTimeNs())
                .isEqualTo(10));
    }

    private static PluginResourceSampler samplerWithOwner(
        ThreadInventory inventory,
        ThreadResourceReader reader,
        PluginResourceAttributionState state,
        ClassLoader loader
    ) {
        return new PluginResourceSampler(
            inventory, reader, new FixedClassLoaderIndex(loader), state);
    }

    private static final class FixedClassLoaderIndex extends PluginClassLoaderIndex {

        private final Map<ClassLoader, String> owners;

        private FixedClassLoaderIndex(ClassLoader loader) {
            super(mock(org.pf4j.PluginWrapper.class));
            owners = Map.of(loader, "plugin-a");
        }

        @Override
        Map<ClassLoader, String> snapshot() {
            return owners;
        }
    }

    private static final class SequenceReader implements ThreadResourceReader {

        private final long[][] values;
        private int index;

        private SequenceReader(long[][] values) {
            this.values = values;
        }

        @Override
        public ThreadResourceSnapshot read(long[] threadIds) {
            long[] next = values[index++];
            return new ThreadResourceSnapshot(
                true, true, new long[] {next[0]}, new long[] {next[1]});
        }
    }
}
