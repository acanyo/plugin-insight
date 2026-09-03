<script setup lang="ts">
import { VAlert } from '@halo-dev/components'
import { computed } from 'vue'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import MetricTrendChart, { type ChartSeries } from '@/components/dashboard/MetricTrendChart.vue'
import type { ChartPoint, TrendsResponse } from '@/types/insight'
import { ratioToPercent } from '@/utils/format'

const props = defineProps<{
  trends?: TrendsResponse
  loading: boolean
  error?: string
}>()

const emit = defineEmits<{ retry: [] }>()

const systemSeries: ChartSeries[] = [
  { key: 'systemCpuPercent', label: '系统 CPU', color: '#087a8e' },
  { key: 'heapUsedPercent', label: 'JVM 堆', color: '#16845b' },
]

const requestSeries: ChartSeries[] = [
  { key: 'averageLatencyMs', label: '平均', color: '#087a8e' },
  { key: 'p95Ms', label: 'P95', color: '#a96614' },
  { key: 'p99Ms', label: 'P99', color: '#bf3f45' },
]

const systemPoints = computed<ChartPoint[]>(() =>
  (props.trends?.system ?? []).map((point) => ({
    timestampMs: point.timestampMs,
    systemCpuPercent: point.systemCpuPercent,
    heapUsedPercent: point.heapMaxBytes
      ? ratioToPercent(point.heapUsedBytes / point.heapMaxBytes)
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
    <section class="insight-section">
      <SectionHeading title="系统负载趋势" description="系统 CPU 与 JVM 堆使用率" />
      <SectionState
        :loading="!trends && loading"
        :error="!trends ? error : undefined"
        :empty="!loading && !error && !systemPoints.length"
        empty-title="所选时间范围内没有系统样本"
        @retry="emit('retry')"
      >
        <MetricTrendChart
          :points="systemPoints"
          :series="systemSeries"
          mode="percent"
          chart-label="系统 CPU 和 JVM 堆内存使用率趋势"
        />
      </SectionState>
    </section>

    <section class="insight-section">
      <SectionHeading title="请求延迟趋势" description="平均响应时间与 P95、P99" />
      <SectionState
        :loading="!trends && loading"
        :error="!trends ? error : undefined"
        :empty="!loading && !error && !requestPoints.length"
        empty-title="所选时间范围内没有请求样本"
        @retry="emit('retry')"
      >
        <MetricTrendChart
          :points="requestPoints"
          :series="requestSeries"
          mode="latency"
          chart-label="请求平均响应时间、P95 和 P99 趋势"
        />
      </SectionState>
    </section>
  </div>
</template>

<style scoped>
.overview-trends {
  display: grid;
  min-width: 0;
  gap: 14px;
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
