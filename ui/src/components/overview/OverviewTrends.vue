<script setup lang="ts">
import { VAlert } from '@halo-dev/components'
import { computed } from 'vue'

import type { ChartSeries } from '@/components/dashboard/MetricTrendChart.vue'
import OverviewTrendCard from '@/components/overview/OverviewTrendCard.vue'
import type { ChartPoint, TrendsResponse } from '@/types/insight'
import { ratioToPercent } from '@/utils/format'

const props = defineProps<{
  trends?: TrendsResponse
  loading: boolean
  error?: string
}>()

const emit = defineEmits<{ retry: [] }>()

const cpuSeries: ChartSeries[] = [
  { key: 'systemCpuPercent', label: '系统 CPU', color: '#2563eb' },
  { key: 'processCpuPercent', label: 'Halo 进程', color: '#0891b2' },
]

const capacitySeries: ChartSeries[] = [
  { key: 'heapUsedPercent', label: 'JVM 堆', color: '#2563eb' },
  { key: 'memoryUsedPercent', label: '系统内存', color: '#0891b2' },
  { key: 'diskUsedPercent', label: '磁盘', color: '#64748b' },
]

const requestSeries: ChartSeries[] = [
  { key: 'averageLatencyMs', label: '平均', color: '#2563eb' },
  { key: 'p95Ms', label: 'P95', color: '#d97706' },
  { key: 'p99Ms', label: 'P99', color: '#dc2626' },
]

const threadSeries: ChartSeries[] = [
  { key: 'liveThreads', label: '活动', color: '#2563eb' },
  { key: 'daemonThreads', label: '守护', color: '#0891b2' },
  { key: 'peakThreads', label: '峰值', color: '#64748b' },
  { key: 'deadlockedThreads', label: '死锁', color: '#dc2626' },
]

const gcSeries: ChartSeries[] = [{ key: 'gcPauseMs', label: 'GC 停顿', color: '#2563eb' }]

const throughputSeries: ChartSeries[] = [
  { key: 'requestsPerMinute', label: '请求 / 分钟', color: '#2563eb' },
]

const errorSeries: ChartSeries[] = [
  { key: 'clientErrorRatePercent', label: '4xx', color: '#d97706' },
  { key: 'serverErrorRatePercent', label: '5xx / 异常', color: '#dc2626' },
]

const cpuPoints = computed<ChartPoint[]>(() =>
  (props.trends?.system ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    systemCpuPercent: point.systemCpuPercent,
    processCpuPercent: point.processCpuPercent,
  })),
)

const capacityPoints = computed<ChartPoint[]>(() =>
  (props.trends?.system ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    heapUsedPercent: point.heapMaxBytes
      ? ratioToPercent(point.heapUsedBytes / point.heapMaxBytes)
      : 0,
    memoryUsedPercent: point.memoryTotalBytes
      ? ratioToPercent(point.memoryUsedBytes / point.memoryTotalBytes)
      : 0,
    diskUsedPercent: point.diskTotalBytes
      ? ratioToPercent(point.diskUsedBytes / point.diskTotalBytes)
      : 0,
  })),
)

const requestPoints = computed<ChartPoint[]>(() =>
  (props.trends?.requests ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    averageLatencyMs: point.averageLatencyMs,
    p95Ms: point.p95Ms,
    p99Ms: point.p99Ms,
  })),
)

const threadPoints = computed<ChartPoint[]>(() =>
  (props.trends?.system ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    liveThreads: point.liveThreads,
    daemonThreads: point.daemonThreads,
    peakThreads: point.peakThreads,
    deadlockedThreads: point.deadlockedThreads,
  })),
)

const gcPoints = computed<ChartPoint[]>(() =>
  (props.trends?.system ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    gcPauseMs: point.gcPauseMs,
  })),
)

const errorPoints = computed<ChartPoint[]>(() =>
  (props.trends?.requests ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    clientErrorRatePercent: point.clientErrorRatePercent,
    serverErrorRatePercent: point.serverErrorRatePercent,
  })),
)

const throughputPoints = computed<ChartPoint[]>(() =>
  (props.trends?.requests ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    requestsPerMinute: point.requestsPerMinute,
  })),
)
</script>

<template>
  <div class="overview-trends">
    <VAlert
      v-if="error && trends"
      class="insight-refresh-error overview-trends__error"
      type="error"
      :title="`趋势数据刷新失败：${error}`"
      :closable="false"
    />
    <OverviewTrendCard
      title="CPU 负载"
      description="宿主系统与 Halo 进程使用率"
      :points="cpuPoints"
      :series="cpuSeries"
      mode="percent"
      :threshold="85"
      threshold-label="高负载参考"
      chart-label="系统 CPU 和 Halo 进程 CPU 使用率趋势"
      empty-title="所选时间范围内没有 CPU 样本"
      :loading="!trends && loading"
      :error="!trends ? error : undefined"
      :from-ms="trends?.fromMs"
      :to-ms="trends?.toMs"
      @retry="emit('retry')"
    />

    <OverviewTrendCard
      title="容量水位"
      description="JVM 堆、系统内存与磁盘占用"
      :points="capacityPoints"
      :series="capacitySeries"
      mode="percent"
      :threshold="85"
      threshold-label="高水位参考"
      chart-label="JVM 堆、系统内存和磁盘使用率趋势"
      empty-title="所选时间范围内没有容量样本"
      :loading="!trends && loading"
      :error="!trends ? error : undefined"
      :from-ms="trends?.fromMs"
      :to-ms="trends?.toMs"
      @retry="emit('retry')"
    />

    <OverviewTrendCard
      title="JVM 活跃度"
      description="活动、守护、峰值与死锁线程"
      :points="threadPoints"
      :series="threadSeries"
      mode="number"
      density="compact"
      chart-label="JVM 活动、守护、峰值与死锁线程趋势"
      empty-title="所选时间范围内没有线程样本"
      :loading="!trends && loading"
      :error="!trends ? error : undefined"
      :from-ms="trends?.fromMs"
      :to-ms="trends?.toMs"
      @retry="emit('retry')"
    />

    <OverviewTrendCard
      title="GC 停顿"
      description="各采样区间的垃圾回收停顿"
      :points="gcPoints"
      :series="gcSeries"
      mode="latency"
      density="compact"
      chart-label="JVM 垃圾回收停顿趋势"
      empty-title="所选时间范围内没有 GC 样本"
      :loading="!trends && loading"
      :error="!trends ? error : undefined"
      :from-ms="trends?.fromMs"
      :to-ms="trends?.toMs"
      @retry="emit('retry')"
    />

    <OverviewTrendCard
      title="请求延迟"
      description="平均响应时间与 P95、P99"
      :points="requestPoints"
      :series="requestSeries"
      mode="latency"
      chart-label="请求平均响应时间、P95 和 P99 趋势"
      empty-title="所选时间范围内没有请求样本"
      :loading="!trends && loading"
      :error="!trends ? error : undefined"
      :from-ms="trends?.fromMs"
      :to-ms="trends?.toMs"
      @retry="emit('retry')"
    />

    <OverviewTrendCard
      title="请求吞吐"
      description="按实际时间跨度折算的每分钟请求量"
      :points="throughputPoints"
      :series="throughputSeries"
      mode="number"
      density="compact"
      chart-label="每分钟请求吞吐趋势"
      empty-title="所选时间范围内没有请求样本"
      :loading="!trends && loading"
      :error="!trends ? error : undefined"
      :from-ms="trends?.fromMs"
      :to-ms="trends?.toMs"
      @retry="emit('retry')"
    />

    <OverviewTrendCard
      title="请求错误率"
      description="区分客户端拒绝与服务端异常"
      :points="errorPoints"
      :series="errorSeries"
      mode="percent"
      percent-scale="adaptive"
      :threshold="5"
      threshold-label="5xx 告警参考"
      chart-label="4xx 与 5xx 或异常请求错误率趋势"
      empty-title="所选时间范围内没有请求样本"
      :loading="!trends && loading"
      :error="!trends ? error : undefined"
      :from-ms="trends?.fromMs"
      :to-ms="trends?.toMs"
      @retry="emit('retry')"
    />
  </div>
</template>

<style scoped>
.overview-trends {
  display: grid;
  min-width: 0;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-trends__error {
  grid-column: 1 / -1;
}

@media (max-width: 820px) {
  .overview-trends {
    grid-template-columns: 1fr;
  }
}
</style>
