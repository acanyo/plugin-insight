import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { diagnosticGet } from '@/api/diagnostics'
import PluginBaselineComparison from '@/components/plugins/PluginBaselineComparison.vue'
import type {
  PluginBaselineComparison as Comparison,
  PluginBaselineSummary,
} from '@/types/diagnostics'
vi.mock('@/api/diagnostics', () => ({ diagnosticGet: vi.fn<typeof diagnosticGet>() }))
vi.mock('@halo-dev/components', () => ({
  VButton: { template: '<button type="button"><slot /></button>' },
  VAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
}))
describe('PluginBaselineComparison', () => {
  it('shows coverage and unavailable values, then requests the chosen reference and range', async () => {
    const summary: PluginBaselineSummary = {
      fromMs: 0,
      toMs: 900_000,
      observedMinutes: 2,
      expectedMinutes: 15,
      coveragePercent: 13.3,
      samples: 4,
      sufficient: false,
      cpuCorePercent: null,
      allocatedBytesPerMinute: null,
      requests: 0,
      errorRatePercent: null,
      averageRequestMs: null,
      maximumThreads: 4,
      cpuSamples: 0,
      allocationSamples: 0,
      cpuObservedMinutes: 0,
      allocationObservedMinutes: 0,
    }
    const data: Comparison = {
      available: true,
      reference: 'previous',
      before: summary,
      after: summary,
      samplingComparable: false,
      notices: ['资源覆盖不足，仅展示已有记录'],
    }
    vi.mocked(diagnosticGet).mockReset().mockResolvedValue(data)
    const wrapper = mount(PluginBaselineComparison, { props: { pluginId: 'alpha', range: '15m' } })
    await flushPromises()
    expect(wrapper.text()).toContain('资源覆盖不足')
    expect(wrapper.text()).toMatch(/单核 CPU 估算\s*不可用\s*不可用/)
    await wrapper.get('select').setValue('upgrade')
    await flushPromises()
    expect(diagnosticGet).toHaveBeenLastCalledWith('plugin-baseline', expect.any(AbortSignal), {
      pluginId: 'alpha',
      range: '15m',
      reference: 'upgrade',
    })
    await wrapper.setProps({ range: '1h' })
    await flushPromises()
    expect(diagnosticGet).toHaveBeenLastCalledWith('plugin-baseline', expect.any(AbortSignal), {
      pluginId: 'alpha',
      range: '1h',
      reference: 'upgrade',
    })
    wrapper.unmount()
  })
})
