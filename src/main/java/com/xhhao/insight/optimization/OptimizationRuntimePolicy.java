package com.xhhao.insight.optimization;

import com.xhhao.insight.config.CollectionSettings;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Thread-safe runtime policy consumed by collectors without reading SQLite on every tick. */
@Component
public class OptimizationRuntimePolicy {

    static final int LOW_OVERHEAD_SAMPLE_SECONDS = 30;
    static final int LOW_OVERHEAD_MAX_THREADS = 256;

    private final OptimizationStateRepository repository;
    private final AtomicReference<OptimizationProfile> profile = new AtomicReference<>(
        OptimizationProfile.standard());

    public OptimizationRuntimePolicy(OptimizationStateRepository repository) {
        this.repository = repository;
    }

    public void initialize(boolean storageAvailable) {
        profile.set(storageAvailable ? repository.load() : OptimizationProfile.standard());
    }

    public OptimizationProfile snapshot() {
        return profile.get();
    }

    public OptimizationProfile enable(long nowMs) {
        OptimizationProfile current = profile.get();
        long appliedAt = current.enabled() && current.appliedAtMs() > 0
            ? current.appliedAtMs() : nowMs;
        return persist(new OptimizationProfile(true, appliedAt, nowMs));
    }

    public OptimizationProfile disable(long nowMs) {
        return persist(new OptimizationProfile(false, 0, nowMs));
    }

    public long sampleIntervalMs(CollectionSettings settings) {
        int configured = clamp(settings.getSamplingSeconds(), 5, 300);
        int effective = profile.get().enabled()
            ? optimizedSamplingSeconds(settings) : configured;
        return Duration.ofSeconds(effective).toMillis();
    }

    public int pluginResourceMaxThreads(CollectionSettings settings) {
        int configured = clamp(settings.getPluginResourceMaxThreads(), 32, 2_048);
        return profile.get().enabled()
            ? optimizedPluginThreadLimit(settings) : configured;
    }

    public int effectiveSamplingSeconds(CollectionSettings settings) {
        return Math.toIntExact(sampleIntervalMs(settings) / 1_000);
    }

    public int optimizedSamplingSeconds(CollectionSettings settings) {
        int configured = clamp(settings.getSamplingSeconds(), 5, 300);
        return Math.max(configured, LOW_OVERHEAD_SAMPLE_SECONDS);
    }

    public int optimizedPluginThreadLimit(CollectionSettings settings) {
        int configured = clamp(settings.getPluginResourceMaxThreads(), 32, 2_048);
        return Math.min(configured, LOW_OVERHEAD_MAX_THREADS);
    }

    private OptimizationProfile persist(OptimizationProfile updated) {
        repository.save(updated);
        profile.set(updated);
        return updated;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
