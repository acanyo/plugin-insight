import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import OverviewTrendCard from '@/components/overview/OverviewTrendCard.vue'
import PluginResourceTrends from '@/components/plugins/PluginResourceTrends.vue'
import type { PluginDiagnosticDetail, PluginDiagnosticPoint } from '@/types/diagnostics'

vi.mock('@halo-dev/components', () => ({
  VEmpty: { template: '<div />' },
  VLoading: { template: '<span />' },
  VButton: { template: '<button type="button"><slot /></button>' },
  VAlert: { template: '<div />' },
}))

describe('PluginResourceTrends', () => {
  it('uses measured minutes and leaves unavailable counters out of both curves', () => {
    const point: PluginDiagnosticPoint = {
      atMs: 0,
      intervalMs: 180_000,
      cpuTimeMs: 600,
      allocatedBytes: 1_048_576,
      maximumThreads: 4,
      resourceMinutes: 3,
      cpuSamples: 4,
      allocationSamples: 2,
      samples: 6,
      cpuObservedMinutes: 2,
      allocationObservedMinutes: 1,
      requests: 0,
      errors: 0,
      requestDurationMs: 0,
      maximumRequestMs: 0,
      requestMinutes: 0,
    }
    const detail: PluginDiagnosticDetail = {
      plugin: {
        name: 'alpha',
        displayName: '甲插件',
        version: '1.0',
        enabled: true,
        phase: 'STARTED',
      },
      range: '15m',
      fromMs: 0,
      toMs: 900_000,
      available: true,
      timeline: [
        point,
        { ...point, atMs: 180_000, cpuObservedMinutes: 0, allocationObservedMinutes: 0 },
      ],
      routes: [],
      events: [],
      observations: [],
      coverage: '只覆盖归属线程',
    }
    const wrapper = shallowMount(PluginResourceTrends, { props: { detail } })
    const cards = wrapper.findAllComponents(OverviewTrendCard)
    expect(cards[0]!.props('points')).toEqual([{ timestampMs: 0, cpu: 0.5 }])
    expect(cards[1]!.props('points')).toEqual([{ timestampMs: 0, allocation: 1 }])
    wrapper.unmount()
  })
})
