package com.xhhao.insight.consumer;

import java.util.List;

/** Components are dimension scores; contributions sum to the final score, including ties. */
public record PressureScoreBreakdown(
        double cpuCorePercent,
        double requestBusyPercent,
        int cpuActiveMinutes,
        int threadPresentMinutes,
        int resourceMinutes,
        List<Component> components,
        String formula) {

    public static PressureScoreBreakdown of(
            double cpu,
            double allocation,
            double requests,
            double threads,
            double cpuShare,
            double allocationShare,
            double requestShare,
            double cpuTopRatio,
            double allocationTopRatio,
            double requestTopRatio,
            double cpuCorePercent,
            double allocatedBytesPerMinute,
            double requestBusyPercent,
            int cpuActiveMinutes,
            int threadPresentMinutes,
            int resourceMinutes) {
        double maximum = Math.max(Math.max(cpu, allocation), Math.max(requests, threads));
        double[] scores = {cpu, allocation, requests, threads};
        int tied = 0;
        for (double score : scores) if (score == maximum) tied++;
        String[] ids = {"cpu", "memory-allocation", "request-time", "threads"};
        String[] labels = {"CPU", "内存分配", "请求耗时", "线程驻留"};
        double[] shares = {cpuShare, allocationShare, requestShare, 0};
        double[] persistence = {
            cpuTopRatio,
            allocationTopRatio,
            requestTopRatio,
            resourceMinutes == 0 ? 0 : threadPresentMinutes / (double) resourceMinutes
        };
        double[] intensity = {
            Math.min(1, cpuCorePercent / 10),
            Math.min(1, allocatedBytesPerMinute / (64 * 1024 * 1024d)),
            Math.min(1, requestBusyPercent / 25),
            0.35
        };
        var components = new java.util.ArrayList<Component>();
        for (int i = 0; i < scores.length; i++) {
            components.add(
                    new Component(
                            ids[i],
                            labels[i],
                            scores[i],
                            scores[i] / 16 + (scores[i] == maximum ? maximum * 0.75 / tied : 0),
                            shares[i],
                            persistence[i],
                            intensity[i]));
        }
        return new PressureScoreBreakdown(
                cpuCorePercent,
                requestBusyPercent,
                cpuActiveMinutes,
                threadPresentMinutes,
                resourceMinutes,
                List.copyOf(components),
                "总分 = 最高维度分 × 75% + 四个维度平均分 × 25%。同分的最高维度平分 75% 部分。"
                        + "CPU/分配/请求维度 = 份额 × (0.35 + 0.65 × 首位持续率) × 强度系数。"
                        + "强度在单核 CPU 10%、分配 64 MiB/min、请求累计耗时 15 秒/min 时取满。"
                        + "线程维度 = min(100, 最大驻留线程数 × 8) × 驻留分钟比例 × 0.35；驻留不表示线程一直忙碌。");
    }

    public record Component(
            String id,
            String label,
            double dimensionScore,
            double contribution,
            double sharePercent,
            double persistenceRatio,
            double intensity) {}
}
