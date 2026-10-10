package com.xhhao.insight.api;

import com.xhhao.insight.api.model.PluginRankingItem;
import com.xhhao.insight.api.model.RequestRouteItem;
import com.xhhao.insight.api.model.TrendResponse;
import com.xhhao.insight.config.InsightSettingsService;
import com.xhhao.insight.consumer.PluginConsumerItem;
import com.xhhao.insight.consumer.PluginConsumerService;
import com.xhhao.insight.storage.database.SqliteStorageLifecycle;
import com.xhhao.insight.storage.repository.RequestMetricRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reads bounded historical trends and request-derived rankings. */
@Component
public class PerformanceQueryService {

    private final SqliteStorageLifecycle storage;
    private final InsightSettingsService settings;
    private final SystemTrendService systemTrends;
    private final RequestTrendService requestTrends;
    private final RequestMetricRepository requestMetrics;
    private final PluginRankingService pluginRanking;
    private final PluginConsumerService pluginConsumers;
    private final Clock clock;

    @Autowired
    public PerformanceQueryService(
        SqliteStorageLifecycle storage,
        InsightSettingsService settings,
        SystemTrendService systemTrends,
        RequestTrendService requestTrends,
        RequestMetricRepository requestMetrics,
        PluginRankingService pluginRanking,
        PluginConsumerService pluginConsumers
    ) {
        this(storage, settings, systemTrends, requestTrends, requestMetrics, pluginRanking,
            pluginConsumers, Clock.systemUTC());
    }

    PerformanceQueryService(
        SqliteStorageLifecycle storage,
        InsightSettingsService settings,
        SystemTrendService systemTrends,
        RequestTrendService requestTrends,
        RequestMetricRepository requestMetrics,
        PluginRankingService pluginRanking,
        PluginConsumerService pluginConsumers,
        Clock clock
    ) {
        this.storage = storage;
        this.settings = settings;
        this.systemTrends = systemTrends;
        this.requestTrends = requestTrends;
        this.requestMetrics = requestMetrics;
        this.pluginRanking = pluginRanking;
        this.pluginConsumers = pluginConsumers;
        this.clock = clock;
    }

    public TrendResponse trends(String rangeName) {
        ApiTimeRange range = range(rangeName);
        if (!storage.health().available()) {
            return new TrendResponse(range.name(), range.fromMs(), range.toMs(), List.of(), List.of());
        }
        return new TrendResponse(range.name(), range.fromMs(), range.toMs(),
            systemTrends.find(range), requestTrends.find(range));
    }

    public List<PluginRankingItem> plugins(String rangeName, String sortName) {
        ApiTimeRange range = range(rangeName);
        return pluginRanking.find(range, sortName, storage.health().available());
    }

    public List<PluginConsumerItem> consumers(String rangeName) {
        ApiTimeRange range = range(rangeName);
        if (!storage.health().available()) {
            return List.of();
        }
        return pluginConsumers.find(range.fromMs(), range.toMs());
    }

    public List<RequestRouteItem> routes(String rangeName, String sortName) {
        ApiTimeRange range = range(rangeName);
        if (!storage.health().available()) {
            return List.of();
        }
        RequestRouteRankingMode rankingMode = RequestRouteRankingMode.parse(sortName);
        long slowRequestMillis = rankingMode.slowOnly()
            ? Math.max(1, settings.collection().getSlowRequestMillis())
            : 0;
        return requestMetrics.findTopRoutes(
                range.fromMs(), range.toMs(), rankingMode.storageSort(), 100).stream()
            .map(route -> new RequestRouteItem(
                route.scope(), route.route(), route.method(), route.requestCount(),
                route.errorCount(), route.errorRate() * 100, route.averageDurationUs() / 1_000d,
                route.p95DurationUs() / 1_000d, route.p99DurationUs() / 1_000d,
                route.durationMaxUs() / 1_000d
            ))
            .filter(route -> rankingMode.includes(route.p95Ms(), slowRequestMillis))
            .toList();
    }

    private ApiTimeRange range(String name) {
        return ApiTimeRange.parse(name, clock.millis());
    }

}
