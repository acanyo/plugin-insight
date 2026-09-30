<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { ChartPoint } from '@/types/insight'
import {
  formatDateTime,
  formatLatency,
  formatNumber,
  formatPercent,
  formatTrendAxisTime,
} from '@/utils/format'

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
    percentScale?: 'fixed' | 'adaptive'
    chartLabel: string
    threshold?: number
    thresholdLabel?: string
    fromMs?: number
    toMs?: number
  }>(),
  { mode: 'number', percentScale: 'fixed', thresholdLabel: '参考线' },
)

const DEFAULT_WIDTH = 760
const MINIMUM_WIDTH = 280
const height = 190
const padding = { top: 16, right: 20, bottom: 28, left: 46 }
const plotElement = ref<HTMLElement>()
const viewportWidth = ref(DEFAULT_WIDTH)
const width = computed(() => Math.max(MINIMUM_WIDTH, viewportWidth.value))
const chartWidth = computed(() => width.value - padding.left - padding.right)
const chartHeight = height - padding.top - padding.bottom
const visibleKeys = ref(new Set(props.series.map((item) => item.key)))
const hoveredIndex = ref<number | null>(null)
const interactionFocused = ref(false)
let resizeObserver: ResizeObserver | undefined

onMounted(() => {
  const element = plotElement.value
  if (!element) return
  const measuredWidth = element.getBoundingClientRect().width
  if (measuredWidth > 0) viewportWidth.value = measuredWidth
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(([entry]) => {
    if (entry && entry.contentRect.width > 0) viewportWidth.value = entry.contentRect.width
  })
  resizeObserver.observe(element)
})

onBeforeUnmount(() => resizeObserver?.disconnect())

watch(
  () => props.series.map((item) => item.key),
  (keys, previousKeys) => {
    const current = new Set(keys)
    const previous = new Set(previousKeys)
    const next = new Set([...visibleKeys.value].filter((key) => current.has(key)))
    keys.filter((key) => !previous.has(key)).forEach((key) => next.add(key))
    if (!next.size && keys[0]) next.add(keys[0])
    visibleKeys.value = next
  },
)

const visibleSeries = computed(() => props.series.filter((item) => visibleKeys.value.has(item.key)))
const orderedPoints = computed(() =>
  [...props.points].sort((left, right) => left.timestampMs - right.timestampMs),
)

function numericValue(point: ChartPoint, key: string): number {
  const value = point[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

const bounds = computed(() => {
  let dataMax = 1
  orderedPoints.value.forEach((point) => {
    props.series.forEach((series) => {
      dataMax = Math.max(dataMax, numericValue(point, series.key))
    })
  })
  const threshold = normalizedThreshold.value ?? 0
  if (props.mode === 'percent') {
    const max =
      props.percentScale === 'adaptive' ? Math.min(100, Math.max(dataMax, threshold) * 1.2) : 100
    return { min: 0, max }
  }
  return { min: 0, max: Math.max(dataMax, threshold) * 1.12 }
})

const normalizedThreshold = computed(() => {
  if (typeof props.threshold !== 'number' || !Number.isFinite(props.threshold)) return null
  return Math.max(0, props.threshold)
})

const timeBounds = computed(() => {
  if (
    Number.isFinite(props.fromMs) &&
    Number.isFinite(props.toMs) &&
    (props.toMs ?? 0) > (props.fromMs ?? 0)
  ) {
    return { min: props.fromMs!, max: props.toMs! }
  }
  const first = orderedPoints.value[0]
  const last = orderedPoints.value[orderedPoints.value.length - 1]
  if (!first || !last) return null
  return { min: first.timestampMs, max: last.timestampMs }
})

function xPositionForTimestamp(timestampMs: number): number {
  const domain = timeBounds.value
  if (!domain || domain.max <= domain.min) return padding.left + chartWidth.value / 2
  const ratio = (timestampMs - domain.min) / (domain.max - domain.min)
  return padding.left + Math.max(0, Math.min(1, ratio)) * chartWidth.value
}

function coordinates(point: ChartPoint, key: string): [number, number] {
  const x = xPositionForTimestamp(point.timestampMs)
  const value = numericValue(point, key)
  const span = Math.max(bounds.value.max - bounds.value.min, 1)
  const y = padding.top + chartHeight - ((value - bounds.value.min) / span) * chartHeight
  return [x, y]
}

function xPosition(index: number): number {
  const point = orderedPoints.value[index]
  return point ? xPositionForTimestamp(point.timestampMs) : padding.left
}

function pathFor(key: string): string {
  return orderedPoints.value
    .map((point, index) => {
      const [x, y] = coordinates(point, key)
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
}

function areaPathFor(key: string): string {
  if (!orderedPoints.value.length) return ''
  const line = pathFor(key)
  const first = coordinates(orderedPoints.value[0]!, key)
  const last = coordinates(orderedPoints.value[orderedPoints.value.length - 1]!, key)
  const baseline = padding.top + chartHeight
  return `${line} L ${last[0].toFixed(2)} ${baseline} L ${first[0].toFixed(2)} ${baseline} Z`
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
  const domain = timeBounds.value
  if (!domain) return []
  const timestamps =
    domain.max === domain.min
      ? [domain.min]
      : [domain.min, domain.min + (domain.max - domain.min) / 2, domain.max]
  return timestamps.map((timestampMs) => ({
    x: xPositionForTimestamp(timestampMs),
    label: formatTrendAxisTime(timestampMs, domain.max - domain.min),
  }))
})

const endpoints = computed(() => {
  const point = orderedPoints.value[orderedPoints.value.length - 1]
  if (!point) return []
  return visibleSeries.value.map((series) => {
    const [x, y] = coordinates(point, series.key)
    return { ...series, x, y }
  })
})

const thresholdY = computed(() => {
  const threshold = normalizedThreshold.value
  if (threshold === null) return null
  const bounded = Math.max(bounds.value.min, Math.min(bounds.value.max, threshold))
  const span = Math.max(bounds.value.max - bounds.value.min, 1)
  return padding.top + chartHeight - ((bounded - bounds.value.min) / span) * chartHeight
})

const tooltip = computed(() => {
  const index = hoveredIndex.value
  const point = index === null ? undefined : orderedPoints.value[index]
  if (index === null || !point) return null
  const x = xPosition(index)
  const percent = (x / width.value) * 100
  return {
    point,
    style: {
      left: `${percent}%`,
      transform:
        percent < 18 ? 'translateX(0)' : percent > 82 ? 'translateX(-100%)' : 'translateX(-50%)',
    },
    values: visibleSeries.value.map((item) => ({
      ...item,
      value: formatAxis(numericValue(point, item.key)),
    })),
  }
})

const hoverGuide = computed(() => {
  const index = hoveredIndex.value
  const point = index === null ? undefined : orderedPoints.value[index]
  if (index === null || !point) return null
  return {
    x: xPosition(index),
    points: visibleSeries.value.map((item) => {
      const [x, y] = coordinates(point, item.key)
      return { ...item, x, y }
    }),
  }
})

const cursorValueText = computed(() => {
  const point = orderedPoints.value[hoveredIndex.value ?? 0]
  if (!point) return undefined
  const values = visibleSeries.value
    .map((item) => `${item.label} ${formatAxis(numericValue(point, item.key))}`)
    .join('，')
  return `${formatDateTime(point.timestampMs)}，${values}`
})

const interactionStyle = {
  left: `${padding.left}px`,
  right: `${padding.right}px`,
  top: `${padding.top}px`,
  bottom: `${padding.bottom}px`,
}

function toggleSeries(key: string): void {
  const next = new Set(visibleKeys.value)
  if (next.has(key)) {
    if (next.size === 1) return
    next.delete(key)
  } else {
    next.add(key)
  }
  visibleKeys.value = next
}

function canToggleSeries(key: string): boolean {
  return !(visibleKeys.value.has(key) && visibleSeries.value.length === 1)
}

function nearestPointIndex(timestampMs: number): number {
  const points = orderedPoints.value
  if (points.length <= 1) return 0
  let low = 0
  let high = points.length - 1
  while (low < high) {
    const middle = Math.floor((low + high) / 2)
    if (points[middle]!.timestampMs < timestampMs) low = middle + 1
    else high = middle
  }
  if (low === 0) return 0
  const previous = points[low - 1]!
  const current = points[low]!
  return timestampMs - previous.timestampMs <= current.timestampMs - timestampMs ? low - 1 : low
}

function selectAtClientX(clientX: number, element: HTMLElement): void {
  const rectangle = element.getBoundingClientRect()
  const domain = timeBounds.value
  if (!domain || rectangle.width <= 0) return
  const ratio = Math.max(0, Math.min(1, (clientX - rectangle.left) / rectangle.width))
  const timestampMs = domain.min + (domain.max - domain.min) * ratio
  hoveredIndex.value = nearestPointIndex(timestampMs)
}

function handlePointer(event: PointerEvent): void {
  selectAtClientX(event.clientX, event.currentTarget as HTMLElement)
}

function handleTouch(event: TouchEvent): void {
  const touch = event.touches[0] ?? event.changedTouches[0]
  if (touch) selectAtClientX(touch.clientX, event.currentTarget as HTMLElement)
}

function handleInteractionFocus(): void {
  interactionFocused.value = true
  if (hoveredIndex.value === null && orderedPoints.value.length) hoveredIndex.value = 0
}

function handleInteractionBlur(): void {
  interactionFocused.value = false
  hoveredIndex.value = null
}

function handlePlotLeave(): void {
  if (!interactionFocused.value) hoveredIndex.value = null
}

function handleInteractionKeydown(event: KeyboardEvent): void {
  const lastIndex = orderedPoints.value.length - 1
  if (lastIndex < 0) return
  const currentIndex = hoveredIndex.value ?? 0
  let nextIndex = currentIndex
  if (event.key === 'ArrowLeft') nextIndex = Math.max(0, currentIndex - 1)
  else if (event.key === 'ArrowRight') nextIndex = Math.min(lastIndex, currentIndex + 1)
  else if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = lastIndex
  else return
  event.preventDefault()
  hoveredIndex.value = nextIndex
}
</script>

<template>
  <div class="trend-chart">
    <div class="trend-chart__legend" role="group" aria-label="图表数据系列">
      <template v-for="item in series" :key="String(item.key)">
        <button
          v-if="series.length > 1"
          type="button"
          class="trend-chart__legend-item"
          :class="{ 'is-muted': !visibleKeys.has(item.key) }"
          :aria-pressed="visibleKeys.has(item.key)"
          :disabled="!canToggleSeries(item.key)"
          :title="
            canToggleSeries(item.key)
              ? `${visibleKeys.has(item.key) ? '隐藏' : '显示'}${item.label}`
              : '至少保留一个数据系列'
          "
          @click="toggleSeries(item.key)"
        >
          <i :style="{ backgroundColor: item.color }"></i>
          <span>{{ item.label }}</span>
        </button>
        <span v-else class="trend-chart__legend-label">
          <i :style="{ backgroundColor: item.color }"></i>
          <span>{{ item.label }}</span>
        </span>
      </template>
    </div>
    <div ref="plotElement" class="trend-chart__plot" @mouseleave="handlePlotLeave">
      <svg
        class="trend-chart__svg"
        :viewBox="`0 0 ${width} ${height}`"
        preserveAspectRatio="xMinYMin meet"
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
        <g v-if="thresholdY !== null" class="trend-chart__threshold">
          <line :x1="padding.left" :x2="width - padding.right" :y1="thresholdY" :y2="thresholdY" />
          <text :x="width - padding.right" :y="thresholdY - 5" text-anchor="end">
            {{ thresholdLabel }} {{ formatAxis(normalizedThreshold ?? 0) }}
          </text>
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
          v-for="item in visibleSeries"
          :key="`area-${String(item.key)}`"
          :d="areaPathFor(item.key)"
          class="trend-chart__area"
          :fill="item.color"
        />
        <path
          v-for="item in visibleSeries"
          :key="String(item.key)"
          :d="pathFor(item.key)"
          class="trend-chart__line"
          :data-series-key="item.key"
          fill="none"
          :stroke="item.color"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
        />
        <circle
          v-for="endpoint in endpoints"
          :key="`endpoint-${endpoint.key}`"
          :cx="endpoint.x"
          :cy="endpoint.y"
          r="3.5"
          :fill="endpoint.color"
          stroke="var(--insight-surface)"
          stroke-width="2"
          vector-effect="non-scaling-stroke"
        />
        <g v-if="hoverGuide" class="trend-chart__hover-guide" aria-hidden="true">
          <line
            :x1="hoverGuide.x"
            :x2="hoverGuide.x"
            :y1="padding.top"
            :y2="padding.top + chartHeight"
          />
          <circle
            v-for="item in hoverGuide.points"
            :key="`hover-${item.key}`"
            :cx="item.x"
            :cy="item.y"
            r="4"
            :fill="item.color"
            stroke="var(--insight-surface)"
            stroke-width="2"
            vector-effect="non-scaling-stroke"
          />
        </g>
      </svg>
      <div
        v-if="orderedPoints.length"
        class="trend-chart__interaction-layer"
        :style="interactionStyle"
        role="slider"
        tabindex="0"
        :aria-label="`${chartLabel}数据游标`"
        :aria-valuemin="1"
        :aria-valuemax="orderedPoints.length"
        :aria-valuenow="(hoveredIndex ?? 0) + 1"
        :aria-valuetext="cursorValueText"
        @pointerdown="handlePointer"
        @pointermove="handlePointer"
        @touchstart="handleTouch"
        @touchmove="handleTouch"
        @focus="handleInteractionFocus"
        @blur="handleInteractionBlur"
        @keydown="handleInteractionKeydown"
      ></div>
      <div v-if="tooltip" class="trend-chart__tooltip" role="tooltip" :style="tooltip.style">
        <div class="trend-chart__tooltip-time">{{ formatDateTime(tooltip.point.timestampMs) }}</div>
        <div
          v-for="item in tooltip.values"
          :key="String(item.key)"
          class="trend-chart__tooltip-row"
        >
          <i :style="{ backgroundColor: item.color }"></i>
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.trend-chart {
  min-width: 0;
  padding: 11px 14px 10px;
}

.trend-chart__legend {
  display: flex;
  min-height: 28px;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  flex-wrap: wrap;
  color: var(--insight-muted);
  font-size: var(--insight-text-xs);
}

.trend-chart__legend-item,
.trend-chart__legend-label {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  gap: 6px;
  padding: 0 7px;
  color: inherit;
  font: inherit;
}

.trend-chart__legend-item {
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  transition:
    border-color 140ms ease,
    background-color 140ms ease,
    color 140ms ease;
}

.trend-chart__legend-item:hover,
.trend-chart__legend-item:focus-visible {
  border-color: var(--insight-border-strong);
  background: var(--insight-surface-muted);
  outline: none;
}

.trend-chart__legend-item:disabled {
  cursor: default;
}

.trend-chart__legend-item:disabled:hover {
  border-color: transparent;
  background: transparent;
}

.trend-chart__legend-item.is-muted {
  color: var(--insight-subtle);
  opacity: 0.68;
}

.trend-chart__legend-item.is-muted i {
  opacity: 0.4;
}

.trend-chart__legend-item i,
.trend-chart__legend-label i {
  display: inline-flex;
  width: 15px;
  height: 3px;
  border-radius: 999px;
}

.trend-chart__plot {
  position: relative;
}

.trend-chart__svg {
  display: block;
  width: 100%;
  height: 190px;
  overflow: visible;
}

.trend-chart__grid line {
  stroke: #e5e7eb;
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.trend-chart__area {
  opacity: 0.07;
}

.trend-chart__threshold line {
  stroke: var(--insight-amber);
  stroke-dasharray: 4 4;
  stroke-width: 1;
  opacity: 0.8;
  vector-effect: non-scaling-stroke;
}

.trend-chart__threshold text {
  fill: var(--insight-amber);
  font-size: 9px;
}

.trend-chart__interaction-layer {
  position: absolute;
  z-index: 1;
  cursor: crosshair;
  touch-action: pan-y;
  user-select: none;
}

.trend-chart__interaction-layer:focus-visible {
  border-radius: 2px;
  outline: 2px solid var(--insight-primary);
  outline-offset: 1px;
}

.trend-chart__hover-guide line {
  stroke: var(--insight-border-strong);
  stroke-dasharray: 2 3;
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.trend-chart__tooltip {
  position: absolute;
  top: 10px;
  z-index: 2;
  min-width: 144px;
  padding: 8px 10px;
  border: 1px solid var(--insight-border-strong);
  border-radius: 5px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 8px 22px rgb(15 23 42 / 12%);
  color: var(--insight-ink);
  pointer-events: none;
}

.trend-chart__tooltip-time {
  margin-bottom: 6px;
  color: var(--insight-subtle);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
}

.trend-chart__tooltip-row {
  display: grid;
  grid-template-columns: 10px 1fr auto;
  align-items: center;
  gap: 6px;
  min-height: 18px;
  font-size: var(--insight-text-xs);
}

.trend-chart__tooltip-row i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.trend-chart__tooltip-row strong {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: var(--insight-text-xs);
  font-weight: 600;
}

.trend-chart text {
  fill: var(--insight-subtle);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
}

@media (max-width: 640px) {
  .trend-chart {
    padding-inline: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trend-chart__legend-item {
    transition: none;
  }
}
</style>
