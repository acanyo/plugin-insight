<script setup lang="ts">
import { computed } from 'vue'

import type { ChartPoint } from '@/types/insight'
import { formatDateTime, formatLatency, formatNumber, formatPercent } from '@/utils/format'

export interface ChartSeries {
  key: string
  label: string
  color: string
}

const props = withDefaults(
  defineProps<{
    points: ChartPoint[]
    series: ChartSeries[]
    mode?: 'percent' | 'latency' | 'number'
    chartLabel: string
  }>(),
  { mode: 'number' },
)

const width = 760
const height = 190
const padding = { top: 16, right: 20, bottom: 28, left: 46 }
const chartWidth = width - padding.left - padding.right
const chartHeight = height - padding.top - padding.bottom

function numericValue(point: ChartPoint, key: string): number {
  const value = point[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

const bounds = computed(() => {
  const values = props.points.flatMap((point) =>
    props.series.map((series) => numericValue(point, series.key)),
  )
  const maxValue = props.mode === 'percent' ? 100 : Math.max(...values, 1)
  return { min: 0, max: maxValue * (props.mode === 'percent' ? 1 : 1.12) }
})

function coordinates(point: ChartPoint, index: number, key: string): [number, number] {
  const x =
    padding.left + (props.points.length <= 1 ? 0 : (index / (props.points.length - 1)) * chartWidth)
  const value = numericValue(point, key)
  const span = Math.max(bounds.value.max - bounds.value.min, 1)
  const y = padding.top + chartHeight - ((value - bounds.value.min) / span) * chartHeight
  return [x, y]
}

function pathFor(key: string): string {
  return props.points
    .map((point, index) => {
      const [x, y] = coordinates(point, index, key)
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
}

function formatAxis(value: number): string {
  if (props.mode === 'percent') return formatPercent(value, 0)
  if (props.mode === 'latency') return formatLatency(value)
  return formatNumber(value)
}

const yTicks = computed(() =>
  [1, 0.5, 0].map((ratio) => ({
    y: padding.top + chartHeight * (1 - ratio),
    value: bounds.value.min + (bounds.value.max - bounds.value.min) * ratio,
  })),
)

const xLabels = computed(() => {
  if (!props.points.length) return []
  const indices = Array.from(
    new Set([0, Math.floor((props.points.length - 1) / 2), props.points.length - 1]),
  )
  return indices.map((index) => ({
    x:
      padding.left +
      (props.points.length <= 1 ? 0 : (index / (props.points.length - 1)) * chartWidth),
    label: formatDateTime(props.points[index]?.timestampMs).slice(6),
  }))
})
</script>

<template>
  <div class="trend-chart">
    <div class="trend-chart__legend" aria-hidden="true">
      <span v-for="item in series" :key="String(item.key)">
        <i :style="{ backgroundColor: item.color }"></i>{{ item.label }}
      </span>
    </div>
    <svg
      class="trend-chart__svg"
      :viewBox="`0 0 ${width} ${height}`"
      preserveAspectRatio="none"
      role="img"
      :aria-label="chartLabel"
    >
      <g class="trend-chart__grid">
        <template v-for="tick in yTicks" :key="tick.y">
          <line :x1="padding.left" :x2="width - padding.right" :y1="tick.y" :y2="tick.y" />
          <text :x="padding.left - 8" :y="tick.y + 4" text-anchor="end">
            {{ formatAxis(tick.value) }}
          </text>
        </template>
      </g>
      <g class="trend-chart__labels">
        <text
          v-for="(label, index) in xLabels"
          :key="`${label.x}-${label.label}`"
          :x="label.x"
          :y="height - 5"
          :text-anchor="index === 0 ? 'start' : index === xLabels.length - 1 ? 'end' : 'middle'"
        >
          {{ label.label }}
        </text>
      </g>
      <path
        v-for="item in series"
        :key="String(item.key)"
        :d="pathFor(item.key)"
        fill="none"
        :stroke="item.color"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        vector-effect="non-scaling-stroke"
      />
    </svg>
  </div>
</template>

<style scoped>
.trend-chart {
  min-width: 0;
  padding: 12px 14px 8px;
}

.trend-chart__legend {
  display: flex;
  min-height: 22px;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  color: var(--insight-muted);
  font-size: 11px;
}

.trend-chart__legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.trend-chart__legend i {
  width: 8px;
  height: 3px;
  border-radius: 1px;
}

.trend-chart__svg {
  display: block;
  width: 100%;
  height: 190px;
  overflow: visible;
}

.trend-chart__grid line {
  stroke: var(--insight-border);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.trend-chart text {
  fill: var(--insight-subtle);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
}

@media (max-width: 640px) {
  .trend-chart {
    padding-inline: 8px;
  }

  .trend-chart__svg {
    height: 164px;
  }
}
</style>
