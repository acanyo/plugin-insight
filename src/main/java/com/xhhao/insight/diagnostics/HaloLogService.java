package com.xhhao.insight.diagnostics;

import com.xhhao.insight.diagnostics.logging.BoundedLogFileReader;
import com.xhhao.insight.diagnostics.logging.LogEntryParser;
import com.xhhao.insight.diagnostics.logging.LogQuerySnapshots;
import com.xhhao.insight.diagnostics.logging.LogQuerySnapshots.Query;
import com.xhhao.insight.diagnostics.logging.LogQuerySnapshots.Snapshot;
import com.xhhao.insight.diagnostics.logging.LogRedactor;
import com.xhhao.insight.diagnostics.logging.PluginLogAttribution;
import com.xhhao.insight.diagnostics.logging.PluginLogClassIndex;
import com.xhhao.insight.halo.HaloHostContext;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Reads configured Halo logs, and delegates bounded file reads and immutable query sessions. */
@Component
public class HaloLogService {
    private final HaloHostContext host;
    private final PluginLogClassIndex plugins;
    private final BoundedLogFileReader reader;
    private final LogQuerySnapshots snapshots;

    @Autowired
    public HaloLogService(
            HaloHostContext host,
            PluginLogClassIndex plugins,
            BoundedLogFileReader reader,
            LogQuerySnapshots snapshots) {
        this.host = host;
        this.plugins = plugins;
        this.reader = reader;
        this.snapshots = snapshots;
    }

    public HaloLogService(HaloHostContext host, PluginLogClassIndex plugins) {
        this(host, plugins, new BoundedLogFileReader(), new LogQuerySnapshots());
    }

    public Result read(int source, String level, String keyword) throws Exception {
        return read(source, level, keyword, 1, 20);
    }

    public Result read(int source, String level, String keyword, int page, int pageSize)
            throws Exception {
        return read(source, level, keyword, page, pageSize, "");
    }

    public Result read(
            int source, String level, String keyword, int page, int pageSize, String pluginId)
            throws Exception {
        return read(source, level, keyword, page, pageSize, pluginId, "", "query");
    }

    public Result read(
            int source,
            String level,
            String keyword,
            int page,
            int pageSize,
            String pluginId,
            String snapshotId,
            String mode)
            throws Exception {
        if (!List.of("all", "ERROR", "WARN", "INFO", "DEBUG", "TRACE").contains(level)
                || keyword.length() > 100
                || page < 1
                || pageSize < 1
                || pageSize > 100
                || !List.of("query", "page", "incremental").contains(mode)) {
            throw new IllegalArgumentException("日志筛选或分页参数无效");
        }
        String selected = pluginId == null ? "" : pluginId.strip();
        var query = new Query(source, level, keyword, selected);
        Snapshot previous = "query".equals(mode) ? null : snapshots.get(snapshotId, query);
        if ("page".equals(mode)) return result(previous, page, pageSize, "");
        var paths = sources();
        var names = paths.stream().map(path -> path.getFileName().toString()).toList();
        var index = plugins.snapshot();
        if (previous == null
                && !selected.isEmpty()
                && index.plugins().stream()
                        .noneMatch(plugin -> plugin.pluginId().equals(selected))) {
            throw new IllegalArgumentException("日志筛选插件不存在，请刷新插件列表");
        }
        if (paths.isEmpty())
            return new Result(
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    page,
                    pageSize,
                    0,
                    false,
                    index.plugins(),
                    selected,
                    0,
                    "未发现 Halo 文件日志。若仅输出到容器 stdout，请配置 logging.file.name 后使用。",
                    "",
                    0);
        if (source < 0 || source >= paths.size()) throw new IllegalArgumentException("日志来源不存在");
        if (previous != null && !previous.cursor().path().equals(paths.get(source))) {
            throw new DiagnosticConflictException("日志来源已变化，请重新查询");
        }
        var window = reader.read(paths.get(source), previous == null ? null : previous.cursor());
        if (previous != null && window.cursor().equals(previous.cursor()) && !window.reset()) {
            return result(previous, page, pageSize, "当前没有新增完整日志行。");
        }
        var entries = new ArrayList<Entry>();
        String content = window.text();
        Entry continuing = null;
        if (previous != null && !window.reset() && !window.gap()) {
            entries.addAll(previous.entries());
            if (!content.isEmpty() && !LogEntryParser.startsEntry(content) && !entries.isEmpty()) {
                continuing = entries.removeLast();
                content = continuing.text() + content;
            }
        }
        var attribution = new PluginLogAttribution(index);
        for (var parsed : LogEntryParser.parse(content)) {
            var association = attribution.associate(parsed);
            var related = new LinkedHashSet<>(association.pluginIds());
            String evidence = association.evidence();
            if (continuing != null) {
                related.addAll(continuing.pluginIds());
                if (!related.isEmpty() && "unknown".equals(evidence))
                    evidence = continuing.attribution();
                continuing = null;
            }
            entries.add(
                    new Entry(
                            parsed.level(),
                            parsed.text(),
                            parsed.loggerName(),
                            List.copyOf(related),
                            evidence));
        }
        String notice =
                window.reset()
                        ? "日志文件发生轮转或重写，已重新定位到当前文件末尾；原快照仍可用于翻页。"
                        : window.gap() ? "新增日志超过 512 KiB，已重新定位末尾，中间部分未读取。" : "";
        var options = new java.util.LinkedHashMap<String, PluginLogClassIndex.PluginOption>();
        if (previous != null)
            previous.plugins().forEach(plugin -> options.put(plugin.pluginId(), plugin));
        index.plugins().forEach(plugin -> options.put(plugin.pluginId(), plugin));
        var snapshot =
                snapshots.put(
                        query,
                        names,
                        entries,
                        List.copyOf(options.values()),
                        window.cursor(),
                        window.truncated() || previous != null && previous.truncated(),
                        index.complete(),
                        notice);
        return result(snapshot, page, pageSize, "");
    }

    private Result result(Snapshot snapshot, int page, int pageSize, String notice) {
        var query = snapshot.query();
        String keyword = query.keyword().toLowerCase(Locale.ROOT);
        var filtered =
                snapshot.entries().stream()
                        .filter(
                                entry ->
                                        "all".equals(query.level())
                                                || entry.level().equals(query.level()))
                        .filter(
                                entry ->
                                        query.pluginId().isEmpty()
                                                || entry.pluginIds().contains(query.pluginId()))
                        .filter(entry -> entry.text().toLowerCase(Locale.ROOT).contains(keyword))
                        .toList();
        var groups = new java.util.LinkedHashMap<String, ExceptionGroup>();
        for (var entry : filtered) {
            if (!entry.text().contains("Exception") && !entry.text().contains("Error")) continue;
            String signature =
                    entry.text()
                            .lines()
                            .filter(
                                    line ->
                                            line.contains("Exception")
                                                    || line.contains("Error")
                                                    || line.stripLeading().startsWith("at "))
                            .limit(9)
                            .collect(java.util.stream.Collectors.joining("\n"));
            if (groups.size() >= 100 && !groups.containsKey(signature)) continue;
            var old = groups.get(signature);
            groups.put(signature, new ExceptionGroup(signature, old == null ? 1 : old.count() + 1));
        }
        int total = filtered.size();
        long offset = (long) (page - 1) * pageSize;
        int from = (int) Math.min(total, offset), to = Math.min(total, from + pageSize);
        int unattributed =
                (int)
                        snapshot.entries().stream()
                                .filter(entry -> entry.pluginIds().isEmpty())
                                .count();
        String coverage = query.pluginId().isEmpty() ? "" : "按 logger 类名和异常堆栈关联，无法归属的记录保留在全部日志。";
        if (!snapshot.indexComplete()) coverage += "部分插件索引不可用，缩写 logger 暂不自动归属。";
        return new Result(
                true,
                snapshot.sources(),
                filtered.reversed().subList(from, to),
                groups.values().stream()
                        .sorted(java.util.Comparator.comparingInt(ExceptionGroup::count).reversed())
                        .limit(20)
                        .toList(),
                snapshot.truncated(),
                page,
                pageSize,
                total,
                to < total,
                snapshot.plugins(),
                query.pluginId(),
                unattributed,
                "快照中共 "
                        + total
                        + " 条匹配日志，当前第 "
                        + page
                        + " 页。翻页固定本次结果，快照最多保留 5 分钟。"
                        + "每次读取最多 512 KiB，快照最多保留 512 Ki 个字符 / 10000 条记录。异常聚合覆盖当前筛选结果。"
                        + "常见凭据已脱敏。"
                        + coverage
                        + snapshot.notice()
                        + notice,
                snapshot.id(),
                snapshot.capturedAtMs());
    }

    static List<Entry> parse(String text) {
        return LogEntryParser.parse(text).stream()
                .map(
                        entry ->
                                new Entry(
                                        entry.level(),
                                        entry.text(),
                                        entry.loggerName(),
                                        List.of(),
                                        "unknown"))
                .toList();
    }

    public static String redact(String text) {
        return LogRedactor.redact(text);
    }

    private List<Path> sources() {
        var result = new LinkedHashSet<Path>();
        try {
            Object root =
                    LoggerFactory.getILoggerFactory()
                            .getClass()
                            .getMethod("getLogger", String.class)
                            .invoke(LoggerFactory.getILoggerFactory(), "ROOT");
            discoverAppenders(root, result, 0);
        } catch (ReflectiveOperationException ignored) {
        }
        if (host.root() != null) {
            String configured = host.root().getEnvironment().getProperty("logging.file.name");
            if (configured != null && !configured.isBlank()) {
                result.add(Path.of(configured).toAbsolutePath().normalize());
            }
        }
        return result.stream()
                .filter(path -> Files.isRegularFile(path) && Files.isReadable(path))
                .limit(5)
                .toList();
    }

    private static void discoverAppenders(Object owner, java.util.Set<Path> paths, int depth) {
        if (depth > 3) {
            return;
        }
        try {
            var iterator =
                    (Iterator<?>) owner.getClass().getMethod("iteratorForAppenders").invoke(owner);
            int count = 0;
            while (iterator.hasNext() && count++ < 20) {
                Object appender = iterator.next();
                try {
                    Object file = appender.getClass().getMethod("getFile").invoke(appender);
                    if (file instanceof String value && !value.isBlank()) {
                        paths.add(Path.of(value).toAbsolutePath().normalize());
                    }
                } catch (ReflectiveOperationException ignored) {
                }
                discoverAppenders(appender, paths, depth + 1);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public record Entry(
            String level,
            String text,
            String loggerName,
            List<String> pluginIds,
            String attribution) {
        public Entry(String level, String text) {
            this(level, text, null, List.of(), "unknown");
        }
    }

    public record ExceptionGroup(String signature, int count) {}

    public record Result(
            boolean available,
            List<String> sources,
            List<Entry> entries,
            List<ExceptionGroup> exceptions,
            boolean truncated,
            int page,
            int pageSize,
            int totalEntries,
            boolean hasNext,
            List<PluginLogClassIndex.PluginOption> plugins,
            String pluginId,
            int unattributedEntries,
            String message,
            String snapshotId,
            long capturedAtMs) {
        public Result(
                boolean available,
                List<String> sources,
                List<Entry> entries,
                List<ExceptionGroup> exceptions,
                boolean truncated,
                int page,
                int pageSize,
                int totalEntries,
                boolean hasNext,
                List<PluginLogClassIndex.PluginOption> plugins,
                String pluginId,
                int unattributedEntries,
                String message) {
            this(
                    available,
                    sources,
                    entries,
                    exceptions,
                    truncated,
                    page,
                    pageSize,
                    totalEntries,
                    hasNext,
                    plugins,
                    pluginId,
                    unattributedEntries,
                    message,
                    "",
                    0);
        }
    }
}
