<script setup lang="ts">
import RiAlarmWarningLine from '~icons/ri/alarm-warning-line'
import RiCpuLine from '~icons/ri/cpu-line'
import RiPulseLine from '~icons/ri/pulse-line'
import RiStackLine from '~icons/ri/stack-line'
import RiTimeLine from '~icons/ri/time-line'

import MetricCard from '@/components/dashboard/MetricCard.vue'
import { REQUEST_HEALTH_THRESHOLDS } from '@/constants/monitoring'
import type { OverviewResponse } from '@/types/insight'
import { formatBytes, formatCompactNumber, formatLatency, formatPercent } from '@/utils/format'

defineProps<{ overview: OverviewResponse }>()
</script>

<template>
  <div class="metric-grid">
    <MetricCard
      label="系统 CPU"
      :value="formatPercent(overview.runtime.systemCpuPercent)"
      :detail="`Halo 进程 ${formatPercent(overview.runtime.processCpuPercent)}`"
      tone="cyan"
    >
      <template #icon><RiCpuLine /></template>
    </MetricCard>
    <MetricCard
      label="JVM 堆内存"
      :value="formatPercent(overview.jvm.heapUsedPercent)"
      :detail="`${formatBytes(overview.jvm.heapUsedBytes)} / ${formatBytes(overview.jvm.heapMaxBytes)}`"
      tone="green"
    >
      <template #icon><RiStackLine /></template>
    </MetricCard>
    <MetricCard
      label="窗口请求"
      :value="formatCompactNumber(overview.requests.requestCount)"
      :detail="`${formatCompactNumber(overview.requests.errorCount)} 次错误`"
    >
      <template #icon><RiPulseLine /></template>
    </MetricCard>
    <MetricCard
      label="P95 / P99"
      :value="formatLatency(overview.requests.p95Ms)"
      :detail="`P99 ${formatLatency(overview.requests.p99Ms)}`"
      :tone="
        overview.requests.p95Ms >= REQUEST_HEALTH_THRESHOLDS.warningP95Ms
          ? 'amber'
          : 'default'
      "
    >
      <template #icon><RiTimeLine /></template>
    </MetricCard>
    <MetricCard
      label="请求错误率"
      :value="formatPercent(overview.requests.errorRatePercent)"
      :detail="`平均 ${formatLatency(overview.requests.averageLatencyMs)}`"
      :tone="
        overview.requests.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.criticalErrorRatePercent
          ? 'red'
          : overview.requests.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.warningErrorRatePercent
            ? 'amber'
            : 'green'
      "
    >
      <template #icon><RiAlarmWarningLine /></template>
    </MetricCard>
    <MetricCard
      label="当前告警"
      :value="`${overview.activeAlerts.length} 条`"
      :detail="overview.activeAlerts.length ? '包含已确认但未恢复的告警' : '所有规则均处于恢复状态'"
      :tone="overview.activeAlerts.length ? 'amber' : 'green'"
    >
      <template #icon><RiAlarmWarningLine /></template>
    </MetricCard>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

@media (max-width: 1250px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
