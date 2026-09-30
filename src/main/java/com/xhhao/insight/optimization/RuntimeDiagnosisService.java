package com.xhhao.insight.optimization;

import com.xhhao.insight.consumer.PluginConsumerItem;
import com.xhhao.insight.metrics.SystemSnapshot;
import com.xhhao.insight.optimization.RuntimeDiagnosis.Finding;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.xhhao.insight.diagnostics.JvmPressureWindow;

/** Diagnoses current JVM and Halo process pressure without performing disruptive actions. */
@Component
public class RuntimeDiagnosisService {
    private final JvmPressureWindow gcWindows;

    @Autowired
    public RuntimeDiagnosisService(JvmPressureWindow gcWindows) { this.gcWindows = gcWindows; }
    public RuntimeDiagnosisService() { this(new JvmPressureWindow()); }

    private static final double WARNING_HEAP_PERCENT = 75;
    private static final double CRITICAL_HEAP_PERCENT = 90;
    private static final double WARNING_PROCESS_CPU_PERCENT = 65;
    private static final double CRITICAL_PROCESS_CPU_PERCENT = 85;
    private static final double WARNING_GC_OVERHEAD_PERCENT = 10;

    public RuntimeDiagnosis analyze(SystemSnapshot system, PluginConsumerItem topConsumer) {
        if (system == null) {
            return new RuntimeDiagnosis(
                "unknown", "等待 JVM 与 Halo 进程采样后生成诊断。",
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new Finding(
                    "waiting-sample", "info", "等待运行样本",
                    "至少完成一次系统采样后，才能判断堆、CPU、GC 和线程压力。")), false);
        }

        double heapPercent = system.heapUsedPercent();
        gcWindows.add(system);
        var recentGc = gcWindows.windows(system.timestampMs()).get(1);
        double gcOverheadPercent = recentGc.overheadPercent();
        List<Finding> findings = new ArrayList<>();
        String level = "healthy";

        if (system.deadlockedThreadCount() > 0) {
            level = "critical";
            findings.add(new Finding(
                "deadlock", "critical", "检测到 JVM 线程死锁",
                "死锁无法通过清理内存解决，请尽快定位线程栈，并在维护窗口处理相关插件或重启 Halo。"));
        }
        if (heapPercent >= CRITICAL_HEAP_PERCENT) {
            level = "critical";
            findings.add(heapFinding("critical"));
        } else if (heapPercent >= WARNING_HEAP_PERCENT) {
            level = elevate(level, "warning");
            findings.add(heapFinding("warning"));
        }
        if (system.processCpuPercent() >= CRITICAL_PROCESS_CPU_PERCENT) {
            level = "critical";
            findings.add(cpuFinding("critical"));
        } else if (system.processCpuPercent() >= WARNING_PROCESS_CPU_PERCENT) {
            level = elevate(level, "warning");
            findings.add(cpuFinding("warning"));
        }
        if (recentGc.sufficient() && gcOverheadPercent >= WARNING_GC_OVERHEAD_PERCENT) {
            level = elevate(level, "warning");
            findings.add(new Finding(
                "gc-overhead", "warning", "GC 时间占比较高",
                "最近 5 分钟 GC 累计耗时增量占观测时长的比例偏高。请结合 GC 后堆趋势排查分配压力。"));
        }
        if (topConsumer != null && topConsumer.sustained()) {
            level = elevate(level, pressureSeverity(topConsumer));
            findings.add(new Finding(
                "top-plugin", pressureSeverity(topConsumer), "发现持续高消耗插件",
                topConsumer.displayName() + " 在最近一小时反复位于资源消耗前列。可先检查配置，或停用后观察 JVM 与请求趋势。"));
        }
        if (findings.isEmpty()) {
            findings.add(new Finding(
                "healthy", "info", "当前无需执行高风险操作",
                "暂未发现持续的 JVM 或插件压力。继续观察堆、GC、CPU 和 P95 趋势即可。"));
        }

        return new RuntimeDiagnosis(
            level, summary(level, topConsumer), system.heapUsedBytes(), system.heapMaxBytes(),
            heapPercent, system.processCpuPercent(), system.systemCpuPercent(), system.gcCount(),
            system.gcTimeMs(), gcOverheadPercent, system.threadCount(),
            system.deadlockedThreadCount(), List.copyOf(findings), recentGc.sufficient());
    }

    private static Finding heapFinding(String severity) {
        return new Finding(
            "heap-pressure", severity, "JVM 堆内存压力较高",
            "插件共享同一个 JVM 堆，无法安全地在线清理某个插件的驻留内存。请先观察 GC 后堆占用，再处理持续高消耗插件或调整 JVM 堆上限。");
    }

    private static Finding cpuFinding(String severity) {
        return new Finding(
            "cpu-pressure", severity, "Halo 进程 CPU 压力较高",
            "优先检查持续高消耗插件、慢接口和后台任务；短时 CPU 峰值不会自动触发插件停用。");
    }

    private static String summary(String level, PluginConsumerItem topConsumer) {
        if ("critical".equals(level) && topConsumer != null && topConsumer.sustained()) {
            return "检测到持续高消耗插件及严重运行压力，建议尽快核查。";
        }
        if ("critical".equals(level)) {
            return "检测到需要尽快处理的 JVM 或 Halo 进程压力。";
        }
        if (topConsumer != null && topConsumer.sustained()) {
            return "检测到持续高消耗插件，建议先处理插件，再观察 JVM 是否恢复。";
        }
        if ("warning".equals(level)) {
            return "检测到运行压力，建议结合下方证据逐项处理。";
        }
        return "当前 JVM 与 Halo 进程运行平稳，未发现必须执行的优化动作。";
    }

    private static String pressureSeverity(PluginConsumerItem consumer) {
        return "high".equals(consumer.pressureLevel()) ? "critical" : "warning";
    }

    private static String elevate(String current, String candidate) {
        return priority(candidate) > priority(current) ? candidate : current;
    }

    private static int priority(String level) {
        return switch (level) {
            case "critical" -> 2;
            case "warning" -> 1;
            default -> 0;
        };
    }

    private static double percentage(long value, long total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(100, value * 100d / total));
    }
}
