<script setup lang="ts">
import { computed } from 'vue'
import RiCpuLine from '~icons/ri/cpu-line'
import RiErrorWarningLine from '~icons/ri/error-warning-line'
import RiNotification3Line from '~icons/ri/notification-3-line'
import RiPulseLine from '~icons/ri/pulse-line'
import RiStackLine from '~icons/ri/stack-line'
import RiTimeLine from '~icons/ri/time-line'

import MetricCard from '@/components/dashboard/MetricCard.vue'
import { REQUEST_HEALTH_THRESHOLDS } from '@/constants/monitoring'
import type { OverviewResponse } from '@/types/insight'
import { summarizeAlerts } from '@/utils/alertStatus'
import { formatBytes, formatCompactNumber, formatLatency, formatPercent } from '@/utils/format'

const props = defineProps<{ overview: OverviewResponse }>()

const alertSummary = computed(() => summarizeAlerts(props.overview.activeAlerts))
const alertTone = computed(() => {
  if (alertSummary.value.pendingCritical > 0) return 'critical'
  if (alertSummary.value.pending.length > 0) return 'warning'
  if (alertSummary.value.acknowledged.length > 0) return 'primary'
  return 'healthy'
})
const alertDetail = computed(() => {
  if (alertSummary.value.pending.length > 0) {
    return `${alertSummary.value.pendingCritical} 严重 · ${alertSummary.value.pendingWarning} 警告${alertSummary.value.acknowledged.length ? ` · 已确认 ${alertSummary.value.acknowledged.length} 条` : ''}`
  }
  if (alertSummary.value.acknowledged.length > 0) {
    return `已确认 ${alertSummary.value.acknowledged.length} 条，等待指标恢复`
  }
  return '所有规则均已恢复'
})
</script>

<template>
  <section class="metric-cluster" aria-label="核心运行指标">
    <header class="metric-cluster__heading">
      <strong>核心指标</strong>
      <span>实时状态与滚动请求窗口，不受历史范围筛选影响</span>
    </header>
    <div class="metric-grid">
      <MetricCard
        label="系统 CPU"
        :value="formatPercent(overview.runtime.systemCpuPercent)"
        :detail="`Halo 进程 ${formatPercent(overview.runtime.processCpuPercent)}`"
        tone="primary"
      >
        <template #icon><RiCpuLine /></template>
      </MetricCard>
      <MetricCard
        label="JVM 堆内存"
        :value="formatPercent(overview.jvm.heapUsedPercent)"
        :detail="`${formatBytes(overview.jvm.heapUsedBytes)} / ${formatBytes(overview.jvm.heapMaxBytes)}`"
        tone="default"
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
          overview.requests.p95Ms >= REQUEST_HEALTH_THRESHOLDS.warningP95Ms ? 'warning' : 'default'
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
            ? 'critical'
            : overview.requests.errorRatePercent >=
                REQUEST_HEALTH_THRESHOLDS.warningErrorRatePercent
              ? 'warning'
              : 'healthy'
        "
      >
        <template #icon><RiErrorWarningLine /></template>
      </MetricCard>
      <MetricCard
        label="待处理告警"
        :value="`${alertSummary.pending.length} 条`"
        :detail="alertDetail"
        :tone="alertTone"
      >
        <template #icon><RiNotification3Line /></template>
      </MetricCard>
    </div>
  </section>
</template>

<style scoped>
.metric-cluster {
  overflow: hidden;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.metric-cluster__heading {
  display: flex;
  min-height: 38px;
  align-items: center;
  gap: 9px;
  padding: 0 15px;
  border-bottom: 1px solid var(--insight-border);
  background: var(--insight-surface-muted);
}

.metric-cluster__heading strong {
  font-size: 11px;
  font-weight: 680;
}

.metric-cluster__heading span {
  color: var(--insight-subtle);
  font-size: 10px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.metric-grid :deep(.metric-card:last-child) {
  border-right: 0;
}

@media (max-width: 1250px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-grid :deep(.metric-card) {
    border-bottom: 1px solid var(--insight-border);
  }

  .metric-grid :deep(.metric-card:nth-child(3n)) {
    border-right: 0;
  }

  .metric-grid :deep(.metric-card:nth-last-child(-n + 3)) {
    border-bottom: 0;
  }
}

@media (max-width: 620px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .metric-grid :deep(.metric-card) {
    border-right: 0;
    border-bottom: 1px solid var(--insight-border);
  }

  .metric-grid :deep(.metric-card:last-child) {
    border-bottom: 0;
  }
}
</style>
