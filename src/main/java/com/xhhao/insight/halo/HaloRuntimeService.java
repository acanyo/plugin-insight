package com.xhhao.insight.halo;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemInfoGetter;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.app.search.SearchEngine;

@Component
public class HaloRuntimeService {

    private final ReactiveExtensionClient extensionClient;
    private final ExtensionGetter extensionGetter;
    private final SystemInfoGetter systemInfoGetter;
    private final AttachmentStorageProbe attachmentStorageProbe;
    private final PluginComponentOwnerResolver componentOwners;

    public HaloRuntimeService(ReactiveExtensionClient extensionClient,
        ExtensionGetter extensionGetter, SystemInfoGetter systemInfoGetter,
        AttachmentStorageProbe attachmentStorageProbe,
        PluginComponentOwnerResolver componentOwners) {
        this.extensionClient = extensionClient;
        this.extensionGetter = extensionGetter;
        this.systemInfoGetter = systemInfoGetter;
        this.attachmentStorageProbe = attachmentStorageProbe;
        this.componentOwners = componentOwners;
    }

    public Mono<HaloRuntimeSnapshot> snapshot() {
        Mono<SystemInfo> systemInfo = systemInfoGetter.get()
            .map(info -> new SystemInfo(info.getVersion() == null ? "unknown" : info.getVersion().toString(),
                info.getActivatedThemeName()))
            .switchIfEmpty(Mono.error(
                new IllegalStateException("Halo system information query returned no result")))
            .onErrorMap(error -> runtimeQueryFailure("Halo system information query failed", error));
        Mono<List<PluginRuntimeInfo>> plugins = extensionClient
            .listAll(Plugin.class, ListOptions.builder().build(), Sort.unsorted())
            .map(HaloRuntimeService::toInfo)
            .sort(Comparator.comparing(PluginRuntimeInfo::displayName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .collectList()
            .onErrorMap(error -> runtimeQueryFailure("Halo plugin status query failed", error));
        Mono<DependencyStatus> database = extensionClient.countBy(Plugin.class,
                ListOptions.builder().build())
            .map(count -> DependencyStatus.healthy("database", "Halo 数据库", "扩展查询正常"))
            .onErrorReturn(DependencyStatus.critical(
                "database", "Halo 数据库", "Halo 核心扩展数据查询失败，不对应单个插件"));
        Mono<DependencyStatus> search = extensionGetter.getEnabledExtension(SearchEngine.class)
            .map(this::searchStatus)
            .defaultIfEmpty(DependencyStatus.warning("search", "搜索服务", "未找到已启用的搜索引擎"))
            .onErrorReturn(DependencyStatus.warning("search", "搜索服务", "无法读取搜索引擎状态"));
        Mono<DependencyStatus> attachments = attachmentStorageProbe.probe()
            .onErrorReturn(DependencyStatus.warning("attachments", "附件存储", "无法读取存储策略"));

        return Mono.zip(systemInfo, plugins, database, search, attachments)
            .map(tuple -> {
                List<PluginRuntimeInfo> runtimePlugins = tuple.getT2();
                List<DependencyStatus> dependencies = List.of(
                    tuple.getT3(), tuple.getT4(), tuple.getT5()).stream()
                    .map(dependency -> resolveProviderNames(dependency, runtimePlugins))
                    .toList();
                return new HaloRuntimeSnapshot(tuple.getT1().version(), tuple.getT1().theme(),
                    runtimePlugins, dependencies);
            });
    }

    private DependencyStatus searchStatus(SearchEngine engine) {
        String implementation = engine.getClass().getSimpleName();
        boolean available = engine.available();
        String baseMessage = available ? "搜索引擎可用" : "搜索引擎暂不可用";
        List<DependencyProvider> providers = componentOwners.pluginId(engine)
            .map(DependencyProvider::new)
            .map(List::of)
            .orElseGet(List::of);
        String message = providers.isEmpty()
            ? baseMessage + "（实现：" + implementation + "）"
            : baseMessage;
        DependencyStatus status = available
            ? DependencyStatus.healthy("search", "搜索服务", message)
            : DependencyStatus.warning("search", "搜索服务", message);
        return status.withProviders(providers);
    }

    private static DependencyStatus resolveProviderNames(
        DependencyStatus dependency,
        List<PluginRuntimeInfo> runtimePlugins
    ) {
        if (dependency.providers().isEmpty()) {
            return dependency;
        }
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (PluginRuntimeInfo plugin : runtimePlugins) {
            if (plugin.name() != null && !plugin.name().isBlank()) {
                displayNames.put(plugin.name(), plugin.displayName());
            }
        }
        List<DependencyProvider> providers = dependency.providers().stream()
            .map(provider -> new DependencyProvider(provider.pluginId(),
                displayNames.get(provider.pluginId())))
            .toList();
        return dependency.withProviders(providers);
    }

    private static RuntimeException runtimeQueryFailure(String message, Throwable failure) {
        return failure instanceof IllegalStateException stateFailure
            && message.equals(stateFailure.getMessage())
            ? stateFailure
            : new IllegalStateException(message, failure);
    }

    private static PluginRuntimeInfo toInfo(Plugin plugin) {
        var metadata = plugin.getMetadata();
        var spec = plugin.getSpec();
        var status = plugin.getStatus();
        return new PluginRuntimeInfo(
            metadata == null ? "unknown" : metadata.getName(),
            spec == null ? null : spec.getDisplayName(),
            spec == null ? null : spec.getVersion(),
            spec == null ? null : spec.getIssues(),
            spec != null && Boolean.TRUE.equals(spec.getEnabled()),
            status == null || status.getPhase() == null ? "UNKNOWN" : status.getPhase().name(),
            status == null ? null : status.getLastStartTime()
        );
    }

    private record SystemInfo(String version, String theme) {}
}
