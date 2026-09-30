package com.xhhao.insight.diagnostics;

import com.xhhao.insight.halo.HaloHostContext;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reads only files already configured by Halo's logging subsystem; never accepts a user path. */
@Component
public class HaloLogService {
    private static final int MAX_BYTES = 512 * 1024;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern LEVEL = Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|ERROR)\\b");
    private static final Pattern SECRET = Pattern.compile(
        "(?i)([\\\"']?(?:password|passwd|token|secret|authorization|cookie|api[_-]?key)[\\\"']?\\s*[:=]\\s*)(?:Bearer\\s+)?[^\\s,;]+"
    );
    private final HaloHostContext host;
    public HaloLogService(HaloHostContext host) { this.host = host; }

    public Result read(int source, String level, String keyword) throws Exception {
        return read(source, level, keyword, 1, DEFAULT_PAGE_SIZE);
    }

    public Result read(int source, String level, String keyword, int page, int pageSize) throws Exception {
        if (!List.of("all", "ERROR", "WARN", "INFO", "DEBUG", "TRACE").contains(level)
            || keyword.length() > 100 || page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("日志筛选或分页参数无效");
        }
        var paths = sources();
        var names = paths.stream().map(path -> path.getFileName().toString()).toList();
        if (paths.isEmpty()) {
            return new Result(false, List.of(), List.of(), List.of(), false, page, pageSize, 0, false,
                "未发现 Halo 文件日志。若当前仅输出到容器 stdout，请为 Halo 配置 logging.file.name 后使用；本功能不读取任意文件。");
        }
        if (source < 0 || source >= paths.size()) { throw new IllegalArgumentException("日志来源不存在"); }
        ByteBuffer buffer = ByteBuffer.allocate(MAX_BYTES);
        boolean truncated;
        try (var channel = FileChannel.open(paths.get(source), StandardOpenOption.READ)) {
            long start = Math.max(0, channel.size() - MAX_BYTES); truncated = start > 0;
            channel.position(start);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
        }
        buffer.flip();
        String text = StandardCharsets.UTF_8.decode(buffer).toString();
        if (truncated && text.contains("\n")) { text = text.substring(text.indexOf('\n') + 1); }
        var entries = parse(text);
        String query = keyword.toLowerCase(Locale.ROOT);
        var filtered = entries.stream().filter(entry -> "all".equals(level) || entry.level().equals(level))
            .filter(entry -> entry.text().toLowerCase(Locale.ROOT).contains(query)).toList();
        var errors = new java.util.LinkedHashMap<String, ExceptionGroup>();
        for (var entry : entries) {
            if (!entry.text().contains("Exception") && !entry.text().contains("Error")) { continue; }
            String signature = entry.text().lines().filter(line -> line.contains("Exception") || line.contains("Error") || line.stripLeading().startsWith("at "))
                .limit(9).collect(java.util.stream.Collectors.joining("\n"));
            if (errors.size() >= 100 && !errors.containsKey(signature)) { continue; }
            var old = errors.get(signature);
            errors.put(signature, new ExceptionGroup(signature, old == null ? 1 : old.count() + 1));
        }
        var groups = errors.values().stream().sorted(java.util.Comparator.comparingInt(ExceptionGroup::count).reversed()).limit(20).toList();
        int totalEntries = filtered.size();
        List<Entry> newestFirst = filtered.reversed();
        long requestedOffset = (long) (page - 1) * pageSize;
        int from = requestedOffset >= totalEntries ? totalEntries : (int) requestedOffset;
        int to = Math.min(totalEntries, from + pageSize);
        boolean hasNext = to < totalEntries;
        return new Result(true, names, newestFirst.subList(from, to), groups, truncated,
            page, pageSize, totalEntries, hasNext,
            "检索当前文件末尾最多 512 KiB，共 " + totalEntries + " 条匹配日志，当前第 " + page
                + " 页。常见凭据已脱敏，但日志可能含业务信息；仅诊断管理员可访问。异常聚合覆盖本次读取范围。");
    }

    static List<Entry> parse(String text) {
        var result = new ArrayList<Entry>();
        String currentLevel = "INFO";
        StringBuilder current = new StringBuilder();
        for (String raw : text.split("\n")) {
            var match = LEVEL.matcher(raw);
            if (match.find() && !raw.startsWith("\t") && !raw.stripLeading().startsWith("at ")) {
                if (!current.isEmpty()) { result.add(new Entry(currentLevel, redact(current.toString()))); }
                current.setLength(0); currentLevel = match.group(1);
            }
            if (current.length() < 16_384) { current.append(raw, 0, Math.min(raw.length(), 4096)).append('\n'); }
        }
        if (!current.isEmpty()) { result.add(new Entry(currentLevel, redact(current.toString()))); }
        return result;
    }

    public static String redact(String text) {
        String headers = text.replaceAll("(?im)(\\b(?:authorization|cookie|set-cookie)\\s*[:=]\\s*)[^\\r\\n]+", "$1[REDACTED]");
        return SECRET.matcher(headers).replaceAll("$1[REDACTED]")
            .replaceAll("(?i)(https?://)[^\\s/@]+:[^\\s/@]+@", "$1[REDACTED]@")
            .replaceAll("(?i)(https?://[^\\s?]+)\\?[^\\s]+", "$1?[REDACTED]");
    }

    private List<Path> sources() {
        var result = new LinkedHashSet<Path>();
        try {
            Object root = LoggerFactory.getILoggerFactory().getClass().getMethod("getLogger", String.class)
                .invoke(LoggerFactory.getILoggerFactory(), "ROOT");
            discoverAppenders(root, result, 0);
        } catch (ReflectiveOperationException ignored) { }
        if (host.root() != null) {
            String configured = host.root().getEnvironment().getProperty("logging.file.name");
            if (configured != null && !configured.isBlank()) { result.add(Path.of(configured).toAbsolutePath().normalize()); }
        }
        return result.stream().filter(path -> Files.isRegularFile(path) && Files.isReadable(path)).limit(5).toList();
    }

    private static void discoverAppenders(Object owner, java.util.Set<Path> paths, int depth) {
        if (depth > 3) { return; }
        try {
            var iterator = (Iterator<?>) owner.getClass().getMethod("iteratorForAppenders").invoke(owner);
            int count = 0;
            while (iterator.hasNext() && count++ < 20) {
                Object appender = iterator.next();
                try {
                    Object file = appender.getClass().getMethod("getFile").invoke(appender);
                    if (file instanceof String value && !value.isBlank()) { paths.add(Path.of(value).toAbsolutePath().normalize()); }
                } catch (ReflectiveOperationException ignored) { }
                discoverAppenders(appender, paths, depth + 1);
            }
        } catch (ReflectiveOperationException ignored) { }
    }

    public record Entry(String level, String text) {}
    public record ExceptionGroup(String signature, int count) {}
    public record Result(boolean available, List<String> sources, List<Entry> entries,
                         List<ExceptionGroup> exceptions, boolean truncated, int page,
                         int pageSize, int totalEntries, boolean hasNext, String message) {}
}
