package com.xhhao.insight.metrics;

import java.util.Optional;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;

/** Resolves request ownership from the actual mapped handler instead of guessing from API groups. */
@Component
public class RequestOwnerResolver {

    private final PluginManager pluginManager;
    private final PluginApiOwnershipIndex apiOwners;
    private final Map<ClassLoader, Optional<String>> handlerOwners =
        Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Halo registers the current plugin wrapper in every plugin application context. The wrapper
     * provides the PF4J manager even though the manager itself is intentionally not a Spring bean.
     */
    public RequestOwnerResolver(
        PluginWrapper currentPlugin,
        PluginApiOwnershipIndex apiOwners
    ) {
        this.pluginManager = currentPlugin.getPluginManager();
        this.apiOwners = apiOwners;
    }

    public Optional<String> pluginId(
        ServerWebExchange exchange,
        boolean allowApiGroupFallback
    ) {
        Object handler = exchange.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler == null) {
            return Optional.empty();
        }
        Class<?> handlerType = handler instanceof HandlerMethod method
            ? method.getBeanType()
            : handler.getClass();
        Optional<String> handlerOwner = handlerOwner(handlerType);
        return handlerOwner.isPresent() || !allowApiGroupFallback
            ? handlerOwner
            : apiOwners.pluginId(apiGroupVersion(exchange));
    }

    private Optional<String> handlerOwner(Class<?> handlerType) {
        ClassLoader classLoader = handlerType.getClassLoader();
        if (classLoader == null) {
            return Optional.empty();
        }
        Optional<String> cached = handlerOwners.get(classLoader);
        if (cached != null) {
            return cached;
        }
        try {
            PluginWrapper owner = pluginManager.whichPlugin(handlerType);
            if (owner == null || owner.getPluginId() == null || owner.getPluginId().isBlank()) {
                Optional<String> missing = Optional.empty();
                handlerOwners.put(classLoader, missing);
                return missing;
            }
            Optional<String> resolved = Optional.of(owner.getPluginId());
            handlerOwners.put(classLoader, resolved);
            return resolved;
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String apiGroupVersion(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith("/apis/")) {
            return "";
        }
        int groupStart = "/apis/".length();
        int groupEnd = path.indexOf('/', groupStart);
        if (groupEnd <= groupStart) {
            return "";
        }
        int versionEnd = path.indexOf('/', groupEnd + 1);
        if (versionEnd < 0) {
            versionEnd = path.length();
        }
        return versionEnd <= groupEnd + 1
            ? ""
            : path.substring(groupStart, versionEnd);
    }
}
