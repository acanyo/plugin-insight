<script setup lang="ts">
import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import MetricTrendChart, { type ChartSeries } from '@/components/dashboard/MetricTrendChart.vue'
import type { ChartPoint } from '@/types/insight'

withDefaults(
  defineProps<{
    title: string
    description: string
    points: ChartPoint[]
    series: ChartSeries[]
    chartLabel: string
    emptyTitle: string
    loading: boolean
    error?: string
    mode?: 'percent' | 'latency' | 'number'
    percentScale?: 'fixed' | 'adaptive'
    density?: 'primary' | 'compact'
    threshold?: number
    thresholdLabel?: string
    fromMs?: number
    toMs?: number
  }>(),
  { mode: 'number', percentScale: 'fixed', density: 'primary', thresholdLabel: '参考线' },
)

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="insight-section overview-trend-card" :class="`overview-trend-card--${density}`">
    <SectionHeading :title="title" :description="description" />
    <SectionState
      :loading="loading"
      :error="error"
      :empty="!loading && !error && !points.length"
      :empty-title="emptyTitle"
      @retry="emit('retry')"
    >
      <MetricTrendChart
        :points="points"
        :series="series"
        :mode="mode"
        :percent-scale="percentScale"
        :threshold="threshold"
        :threshold-label="thresholdLabel"
        :chart-label="chartLabel"
        :from-ms="fromMs"
        :to-ms="toMs"
      />
    </SectionState>
  </section>
</template>

<style scoped>
.overview-trend-card--compact :deep(.section-heading) {
  min-height: 50px;
}

.overview-trend-card--compact :deep(.trend-chart) {
  padding-top: 8px;
  padding-bottom: 6px;
}
</style>
