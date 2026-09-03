package com.xhhao.insight.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RequestPathClassifier {

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern UUID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_TOKEN = Pattern.compile("[0-9a-fA-F]{16,}");
    private static final Pattern OPAQUE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{24,}");
    private static final Pattern SLUG = Pattern.compile(
        "(?=.*[-_])(?=.*[A-Za-z])[A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)+");
    private static final Pattern PATH_SEPARATOR = Pattern.compile("/");
    private static final Pattern PLUGIN_ASSET_PATH = Pattern.compile(
        "^/plugins/[^/]+/assets(?:/.*)?$");
    private static final int MAX_RAW_PATH_LENGTH = 2_048;
    private static final int MAX_SEGMENT_LENGTH = 96;
    private static final int MAX_SEGMENTS_TO_INSPECT = 12;
    private static final int MAX_GENERIC_ROUTE_SEGMENTS = 6;
    private static final Set<String> STATIC_SUFFIXES = Set.of(
        ".js", ".mjs", ".css", ".map", ".json", ".txt", ".xml", ".webmanifest",
        ".wasm", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".bmp", ".webp",
        ".avif", ".mp4", ".webm", ".mov", ".mkv", ".mp3", ".m4a", ".ogg", ".wav",
        ".pdf", ".zip", ".rar", ".7z", ".gz", ".woff", ".woff2", ".ttf", ".eot");
    private static final Set<String> DYNAMIC_PARENT_SEGMENTS = Set.of(
        "archives", "authors", "categories", "comments", "links", "moments", "pages",
        "photos", "posts", "profiles", "tags", "users");
    private static final Set<String> STATIC_ACTION_SEGMENTS = Set.of(
        "archive", "featured", "latest", "new", "popular", "recent", "search");
    private static final Set<String> SAFE_ROOT_SEGMENTS = Set.of(
        "api", "apis", "archives", "authors", "categories", "comments", "console",
        "feed", "healthz", "links", "login", "logout", "moments", "pages", "photos",
        "plugins", "posts", "profiles", "register", "reset-password", "rss", "search",
        "sitemap", "tags", "uc", "users");
    private static final Set<String> SAFE_STRUCTURAL_SEGMENTS = Set.of(
        "archive", "attachments", "comments", "featured", "latest", "new", "popular",
        "recent", "replies", "search", "status");
    /**
     * API groups owned by Halo itself. Other groups are treated as plugin scopes so public
     * plugin APIs such as {@code api.links.halo.run} are not hidden in the core bucket.
     */
    private static final Set<String> HALO_CORE_API_GROUPS = Set.of(
        "api.console.halo.run",
        "api.halo.run",
        "api.notification.halo.run",
        "api.plugin.halo.run",
        "api.storage.halo.run",
        "auth.halo.run",
        "console.api.halo.run",
        "console.api.notification.halo.run",
        "console.api.security.halo.run",
        "console.api.storage.halo.run",
        "content.halo.run",
        "core.halo.run",
        "metrics.halo.run",
        "migration.halo.run",
        "notification.halo.run",
        "plugin.halo.run",
        "rbac.authorization.halo.run",
        "security.halo.run",
        "storage.halo.run",
        "theme.halo.run",
        "uc.api.auth.halo.run",
        "uc.api.content.halo.run",
        "uc.api.halo.run",
        "uc.api.security.halo.run",
        "uc.api.storage.halo.run"
    );

    public Classification classify(String rawPath) {
        String path = boundedPath(rawPath);
        if (path.startsWith("/apis/console.api.insight.halo.run/")) {
            return Classification.ignored();
        }
        List<String> segments = segments(path);
        if (segments.size() >= 4 && "apis".equals(segments.get(0))) {
            String group = segments.get(1);
            String route = apiRoute(segments);
            String scope = scopeFromGroup(group);
            return new Classification(scope, route, true, !"halo-core".equals(scope));
        }
        if (isStatic(path)) {
            return Classification.ignored();
        }
        if (segments.size() >= 2 && "plugins".equals(segments.get(0))) {
            return new Classification(
                segments.get(1), normalizedGenericPath(segments), true, true);
        }
        return new Classification("halo-core", normalizedGenericPath(segments), true, false);
    }

    private static String apiRoute(List<String> segments) {
        List<String> route = new ArrayList<>();
        for (int index = 0; index < Math.min(4, segments.size()); index++) {
            route.add(segments.get(index));
        }
        if (segments.size() > 4) {
            route.add("{name}");
        }
        if (segments.size() > 5) {
            route.add("{subresource}");
        }
        return "/" + String.join("/", route);
    }

    private static String normalizedGenericPath(List<String> segments) {
        if (segments.isEmpty()) {
            return "/";
        }
        List<String> normalized = new ArrayList<>();
        int routeSegmentCount = Math.min(MAX_GENERIC_ROUTE_SEGMENTS, segments.size());
        for (int index = 0; index < routeSegmentCount; index++) {
            String previous = index == 0 ? "" : segments.get(index - 1);
            normalized.add(index == 0
                ? normalizeRootSegment(segments.get(index))
                : normalizeGenericSegment(segments.get(index), previous));
        }
        if (segments.size() > MAX_GENERIC_ROUTE_SEGMENTS) {
            normalized.add("{tail}");
        }
        return "/" + String.join("/", normalized);
    }

    private static String normalizeSegment(String value) {
        return NUMBER.matcher(value).matches() || UUID.matcher(value).matches() ? "{id}" : value;
    }

    private static String normalizeGenericSegment(String value, String previous) {
        String normalized = normalizeSegment(value);
        if (!normalized.equals(value)) {
            return normalized;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String previousLower = previous.toLowerCase(Locale.ROOT);
        if (DYNAMIC_PARENT_SEGMENTS.contains(previousLower)
            && !STATIC_ACTION_SEGMENTS.contains(lower)) {
            return "{slug}";
        }
        if (SAFE_STRUCTURAL_SEGMENTS.contains(lower)
            || STATIC_ACTION_SEGMENTS.contains(lower)) {
            return lower;
        }
        if (HEX_TOKEN.matcher(value).matches() || OPAQUE_TOKEN.matcher(value).matches()) {
            return "{token}";
        }
        if (SLUG.matcher(value).matches() && value.length() >= 12) {
            return "{slug}";
        }
        return "{segment}";
    }

    private static String normalizeRootSegment(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return SAFE_ROOT_SEGMENTS.contains(lower) ? lower : "{route}";
    }

    private static String scopeFromGroup(String group) {
        String normalized = group.toLowerCase(Locale.ROOT);
        if (HALO_CORE_API_GROUPS.contains(normalized)
            || normalized.endsWith(".content.halo.run")) {
            return "halo-core";
        }
        normalized = normalized.replaceFirst("^(console|uc)\\.api\\.", "")
            .replaceFirst("^api\\.", "")
            .replaceFirst("\\.halo\\.run$", "");
        return boundedSegment(normalized.isBlank() ? "halo-core" : normalized);
    }

    private static List<String> segments(String path) {
        return PATH_SEPARATOR.splitAsStream(path)
            .filter(segment -> !segment.isBlank())
            .map(RequestPathClassifier::boundedSegment)
            .limit(MAX_SEGMENTS_TO_INSPECT)
            .toList();
    }

    private static String boundedPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        return rawPath.length() <= MAX_RAW_PATH_LENGTH
            ? rawPath
            : rawPath.substring(0, MAX_RAW_PATH_LENGTH - 1) + "~";
    }

    private static String boundedSegment(String segment) {
        return segment.length() <= MAX_SEGMENT_LENGTH
            ? segment
            : segment.substring(0, MAX_SEGMENT_LENGTH - 1) + "~";
    }

    private static boolean isStatic(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith("/ui-assets/")
            || PLUGIN_ASSET_PATH.matcher(lower).matches()
            || STATIC_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    public record Classification(
        String scope,
        String route,
        boolean monitored,
        boolean inferredPluginScope
    ) {
        static Classification ignored() {
            return new Classification("", "", false, false);
        }
    }
}
