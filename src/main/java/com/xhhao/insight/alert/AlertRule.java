package com.xhhao.insight.alert;

import com.xhhao.insight.config.AlertSettings;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Built-in alert rules and their metric/threshold bindings.
 */
public enum AlertRule {
    SYSTEM_CPU(
        "system.cpu",
        "system.cpu.percent",
        "系统 CPU 使用率过高",
        "%",
        "warning",
        AlertEvaluationInput::systemCpuPercent,
        settings -> percentThreshold(settings.getCpuWarningPercent()),
        false
    ),
    JVM_HEAP(
        "jvm.heap",
        "jvm.heap.used.percent",
        "JVM 堆内存使用率过高",
        "%",
        "warning",
        AlertEvaluationInput::heapUsedPercent,
        settings -> percentThreshold(settings.getHeapWarningPercent()),
        false
    ),
    DISK_USED(
        "system.disk",
        "system.disk.used.percent",
        "磁盘使用率过高",
        "%",
        "warning",
        AlertEvaluationInput::diskUsedPercent,
        settings -> percentThreshold(settings.getDiskWarningPercent()),
        false
    ),
    REQUEST_ERROR_RATE(
        "request.error-rate",
        "http.request.error.percent",
        "请求错误率过高",
        "%",
        "warning",
        AlertEvaluationInput::requestErrorRatePercent,
        settings -> percentThreshold(settings.getErrorRatePercent()),
        false
    ),
    REQUEST_P95(
        "request.p95",
        "http.request.p95.milliseconds",
        "请求 P95 响应时间过高",
        "ms",
        "warning",
        input -> input.requestP95Millis(),
        settings -> Math.max(0, settings.getP95WarningMillis()),
        false
    ),
    JVM_DEADLOCK(
        "jvm.deadlock",
        "jvm.thread.deadlocked.count",
        "检测到 JVM 线程死锁",
        "个",
        "critical",
        input -> input.deadlockedThreadCount(),
        ignored -> 0,
        true
    );

    private final String ruleId;
    private final String metricKey;
    private final String title;
    private final String unit;
    private final String severity;
    private final ToDoubleFunction<AlertEvaluationInput> valueReader;
    private final ToDoubleFunction<AlertSettings> thresholdReader;
    private final boolean strictlyGreater;

    AlertRule(
        String ruleId,
        String metricKey,
        String title,
        String unit,
        String severity,
        ToDoubleFunction<AlertEvaluationInput> valueReader,
        ToDoubleFunction<AlertSettings> thresholdReader,
        boolean strictlyGreater
    ) {
        this.ruleId = ruleId;
        this.metricKey = metricKey;
        this.title = title;
        this.unit = unit;
        this.severity = severity;
        this.valueReader = valueReader;
        this.thresholdReader = thresholdReader;
        this.strictlyGreater = strictlyGreater;
    }

    public String ruleId() {
        return ruleId;
    }

    public String metricKey() {
        return metricKey;
    }

    public String title() {
        return title;
    }

    public String unit() {
        return unit;
    }

    public String severity() {
        return severity;
    }

    public double currentValue(AlertEvaluationInput input) {
        return valueReader.applyAsDouble(input);
    }

    public double threshold(AlertSettings settings) {
        return thresholdReader.applyAsDouble(settings);
    }

    public boolean breached(AlertEvaluationInput input, AlertSettings settings) {
        double value = currentValue(input);
        double threshold = threshold(settings);
        return strictlyGreater ? value > threshold : value >= threshold;
    }

    public String activeSummary(double value, double threshold) {
        return "%s，当前 %s%s，阈值 %s%s".formatted(
            title, displayValue(value), unit, displayValue(threshold), unit);
    }

    public String recoveredSummary(double value, double threshold) {
        return "%s已恢复，当前 %s%s，阈值 %s%s".formatted(
            title, displayValue(value), unit, displayValue(threshold), unit);
    }

    public static Optional<AlertRule> fromRuleId(String ruleId) {
        if (ruleId == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(rule -> rule.ruleId.equals(ruleId))
            .findFirst();
    }

    private String displayValue(double value) {
        if (this == JVM_DEADLOCK) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double percentThreshold(double value) {
        if (!Double.isFinite(value)) {
            return 100;
        }
        return Math.max(0, Math.min(100, value));
    }
}
