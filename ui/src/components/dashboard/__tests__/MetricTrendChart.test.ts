import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import MetricTrendChart, { type ChartSeries } from '@/components/dashboard/MetricTrendChart.vue'
import type { ChartPoint } from '@/types/insight'
import { formatTime } from '@/utils/format'

const points: ChartPoint[] = [
  { timestampMs: 1_756_992_000_000, cpu: 24, heap: 41 },
  { timestampMs: 1_756_992_015_000, cpu: 38, heap: 49 },
]

const series: ChartSeries[] = [
  { key: 'cpu', label: '系统 CPU', color: '#087a8e' },
  { key: 'heap', label: 'JVM 堆', color: '#16845b' },
]

interface ChartOverrides {
  points?: ChartPoint[]
  series?: ChartSeries[]
  mode?: 'percent' | 'latency' | 'number'
  percentScale?: 'fixed' | 'adaptive'
  threshold?: number
  thresholdLabel?: string
  chartLabel?: string
  fromMs?: number
  toMs?: number
}

function mountChart(overrides: ChartOverrides = {}) {
  return mount(MetricTrendChart, {
    props: {
      points,
      series,
      mode: 'percent',
      threshold: 80,
      chartLabel: '系统资源趋势',
      ...overrides,
    },
  })
}

function mockInteractionBounds(element: Element): void {
  vi.spyOn(element, 'getBoundingClientRect').mockReturnValue({
    x: 0,
    y: 0,
    left: 0,
    top: 0,
    right: 100,
    bottom: 100,
    width: 100,
    height: 100,
    toJSON: () => ({}),
  })
}

describe('MetricTrendChart', () => {
  it('renders a threshold and lets an operator hide an individual series', async () => {
    const wrapper = mountChart({ thresholdLabel: '告警阈值' })
    const buttons = wrapper.findAll('.trend-chart__legend-item')

    expect(wrapper.text()).toContain('告警阈值 80%')
    expect(wrapper.findAll('.trend-chart__area')).toHaveLength(2)
    expect(buttons).toHaveLength(2)

    await buttons[0]!.trigger('click')

    expect(buttons[0]!.attributes('aria-pressed')).toBe('false')
    expect(wrapper.findAll('.trend-chart__area')).toHaveLength(1)
    expect(buttons[1]!.attributes()).toHaveProperty('disabled')
  })

  it('uses timestamps rather than array indices for x positions and formats time labels directly', () => {
    const start = points[0]!.timestampMs
    const irregularPoints: ChartPoint[] = [
      { timestampMs: start, cpu: 10 },
      { timestampMs: start + 10_000, cpu: 20 },
      { timestampMs: start + 100_000, cpu: 30 },
    ]
    const wrapper = mountChart({
      points: irregularPoints,
      series: [series[0]!],
      threshold: undefined,
    })

    const path = wrapper.find('.trend-chart__line[data-series-key="cpu"]').attributes('d')
    expect(path).toMatch(/^M 46\.00 [\d.]+ L 115\.40 [\d.]+ L 740\.00 [\d.]+$/)
    expect(wrapper.findAll('.trend-chart__labels text').map((label) => label.text())).toEqual([
      formatTime(start),
      formatTime(start + 50_000),
      formatTime(start + 100_000),
    ])
  })

  it('uses the selected historical window for the x-axis instead of stretching available data', () => {
    const start = points[0]!.timestampMs
    const wrapper = mountChart({
      points: [{ timestampMs: start + 15_000, cpu: 24 }],
      series: [series[0]!],
      threshold: undefined,
      fromMs: start,
      toMs: start + 60_000,
    })

    expect(wrapper.find('.trend-chart__line').attributes('d')).toMatch(/^M 219\.50 /)
    expect(wrapper.findAll('.trend-chart__labels text').map((label) => label.text())).toEqual([
      formatTime(start),
      formatTime(start + 30_000),
      formatTime(start + 60_000),
    ])
  })

  it('uses one interaction overlay regardless of sample count', () => {
    const manyPoints = Array.from({ length: 10_000 }, (_, index) => ({
      timestampMs: points[0]!.timestampMs + index * 15_000,
      cpu: index % 100,
    }))
    const wrapper = mountChart({
      points: manyPoints,
      series: [series[0]!],
      threshold: undefined,
    })

    expect(wrapper.findAll('.trend-chart__interaction-layer')).toHaveLength(1)
    expect(wrapper.findAll('.trend-chart__hit-areas rect')).toHaveLength(0)
  })

  it('selects the nearest sample with pointer and touch input', async () => {
    const wrapper = mountChart()
    const interaction = wrapper.find('.trend-chart__interaction-layer')
    mockInteractionBounds(interaction.element)

    interaction.element.dispatchEvent(
      new MouseEvent('pointermove', { bubbles: true, clientX: 100 }),
    )
    await nextTick()
    expect(wrapper.find('.trend-chart__tooltip').text()).toContain('38%')
    expect(wrapper.find('.trend-chart__hover-guide').exists()).toBe(true)

    const touchEvent = new Event('touchstart', { bubbles: true })
    Object.defineProperty(touchEvent, 'touches', { value: [{ clientX: 0 }] })
    Object.defineProperty(touchEvent, 'changedTouches', { value: [] })
    interaction.element.dispatchEvent(touchEvent)
    await nextTick()

    expect(wrapper.find('.trend-chart__tooltip').text()).toContain('24%')
  })

  it('exposes a keyboard-readable data cursor with chronological navigation', async () => {
    const wrapper = mountChart()
    const interaction = wrapper.find('.trend-chart__interaction-layer')

    await interaction.trigger('focus')
    expect(interaction.attributes('aria-valuenow')).toBe('1')
    expect(interaction.attributes('aria-valuetext')).toContain('系统 CPU 24%')

    await interaction.trigger('keydown', { key: 'ArrowRight' })
    expect(interaction.attributes('aria-valuenow')).toBe('2')
    expect(interaction.attributes('aria-valuetext')).toContain('系统 CPU 38%')
    expect(wrapper.find('.trend-chart__tooltip').text()).toContain('49%')

    await interaction.trigger('keydown', { key: 'Home' })
    expect(interaction.attributes('aria-valuenow')).toBe('1')
    await interaction.trigger('keydown', { key: 'End' })
    expect(interaction.attributes('aria-valuenow')).toBe('2')
  })

  it('supports fixed and adaptive percentage scales', () => {
    const errorPoints: ChartPoint[] = [
      { timestampMs: points[0]!.timestampMs, errorRate: 2 },
      { timestampMs: points[1]!.timestampMs, errorRate: 4 },
    ]
    const errorSeries: ChartSeries[] = [{ key: 'errorRate', label: '错误率', color: '#bf3f45' }]
    const fixed = mountChart({ points: errorPoints, series: errorSeries, threshold: 5 })
    const adaptive = mountChart({
      points: errorPoints,
      series: errorSeries,
      threshold: 5,
      percentScale: 'adaptive',
    })

    expect(fixed.find('.trend-chart__grid text').text()).toBe('100%')
    expect(adaptive.find('.trend-chart__grid text').text()).toBe('6%')
  })

  it('renders a static legend for one series and centers a single sample', () => {
    const wrapper = mountChart({
      points: [{ timestampMs: points[0]!.timestampMs, cpu: 24 }],
      series: [series[0]!],
      threshold: undefined,
    })

    expect(wrapper.findAll('.trend-chart__legend-item')).toHaveLength(0)
    expect(wrapper.find('.trend-chart__legend-label').text()).toBe('系统 CPU')
    expect(wrapper.find('.trend-chart__line').attributes('d')).toMatch(/^M 393\.00 /)
    expect(wrapper.findAll('.trend-chart__labels text')).toHaveLength(1)
  })

  it('preserves hidden series when the series metadata is extended', async () => {
    const wrapper = mountChart()
    await wrapper.findAll('.trend-chart__legend-item')[0]!.trigger('click')

    await wrapper.setProps({
      series: [...series, { key: 'disk', label: '磁盘', color: '#a96614' }],
    })

    const buttons = wrapper.findAll('.trend-chart__legend-item')
    expect(buttons[0]!.attributes('aria-pressed')).toBe('false')
    expect(buttons[2]!.attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('.trend-chart__area')).toHaveLength(2)
  })

  it('does not render an interaction cursor for an empty data set', () => {
    const wrapper = mountChart({ points: [], threshold: undefined })

    expect(wrapper.find('.trend-chart__interaction-layer').exists()).toBe(false)
    expect(wrapper.find('.trend-chart__tooltip').exists()).toBe(false)
  })
})
