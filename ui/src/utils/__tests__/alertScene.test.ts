import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AlertSceneDetails from '@/components/alerts/AlertSceneDetails.vue'
import type { AlertIncident } from '@/types/insight'
import { alertScene } from '@/utils/alertScene'

function incident(detailsJson: string): AlertIncident {
  return {
    id: '1',
    ruleId: 'cpu',
    status: 'RESOLVED',
    severity: 'warning',
    title: 'CPU',
    summary: '已经恢复',
    fingerprint: 'cpu',
    triggeredAtMs: 1,
    updatedAtMs: 2,
    occurrenceCount: 2,
    detailsJson,
  }
}
const scene = {
  capturedAtMs: 1_800_000_000_000,
  sampledAtMs: 0,
  processCpuPercent: null,
  heapUsedPercent: null,
  gcPercent: null,
  p95Ms: null,
  requestCount: 0,
  requestFromMs: 1_799_999_100_000,
  requestToMs: 1_800_000_000_000,
  plugins: [],
  routes: [],
  coverage: '当时没有可用计数',
}

describe('alertScene', () => {
  it('keeps legacy and malformed details out of the scene view', () => {
    for (const payload of [
      '{broken',
      '{}',
      '{"scene":null}',
      JSON.stringify({ scene: { ...scene, capturedAtMs: 'yesterday' } }),
      ' '.repeat(262_145),
    ]) {
      expect(alertScene(incident(payload))).toBeUndefined()
    }
  })
  it('shows the original scene after recovery and preserves unknown measurements', () => {
    const alert = incident(JSON.stringify({ schemaVersion: 2, value: 20, scene }))
    expect(alertScene(alert)?.processCpuPercent).toBeNull()
    const wrapper = mount(AlertSceneDetails, { props: { alert } })
    expect(wrapper.text()).toContain('查看首次触发现场')
    expect(wrapper.text()).toContain('当时没有可用计数')
    expect(wrapper.text()).toMatch(/进程 CPU\s*—/)
    wrapper.unmount()
  })
  it('filters invalid evidence and bounds both evidence lists to five records', () => {
    const plugin = {
      pluginId: 'alpha',
      displayName: '甲',
      pressureScore: 9,
      cpuSharePercent: 0,
      allocatedBytesPerMinute: 0,
      sustained: false,
    }
    const route = { method: 'GET', route: '/test', requests: 1, errors: 0, p95Ms: 100 }
    const data = alertScene(
      incident(
        JSON.stringify({
          scene: {
            ...scene,
            plugins: [null, ...Array(10).fill(plugin)],
            routes: [{}, ...Array(10).fill(route)],
          },
        }),
      ),
    )
    expect(data?.plugins).toHaveLength(5)
    expect(data?.routes).toHaveLength(5)
  })
})
