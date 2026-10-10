import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { diagnosticGet } from '@/api/diagnostics'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import LogInspector from '@/components/diagnostics/LogInspector.vue'
import PluginDetailModal from '@/components/plugins/PluginDetailModal.vue'
import type { PluginDiagnosticDetail } from '@/types/diagnostics'

const { grants } = vi.hoisted(() => ({ grants: new Set<string>() }))
vi.mock('@/api/diagnostics', () => ({ diagnosticGet: vi.fn<typeof diagnosticGet>() }))
vi.mock('@halo-dev/ui-shared', () => ({
  utils: {
    permission: { has: (permissions: string[]) => permissions.every((p) => grants.has(p)) },
  },
}))
vi.mock('@halo-dev/components', () => ({
  VModal: { template: '<section><slot /></section>' },
  VButton: { template: '<button type="button"><slot /></button>' },
  VAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
}))

const detail: PluginDiagnosticDetail = {
  plugin: { name: 'alpha', displayName: '甲插件', version: '1.0', enabled: true, phase: 'STARTED' },
  range: '1h',
  fromMs: 0,
  toMs: 3_600_000,
  available: true,
  timeline: [],
  routes: [],
  events: [],
  observations: [],
  coverage: '只覆盖归属线程',
}

function mountDetail() {
  return mount(PluginDetailModal, {
    props: { pluginId: 'alpha', range: '1h' },
    global: {
      stubs: {
        PluginResourceTrends: true,
        PluginRequestsPanel: true,
        PluginEvidenceHistory: true,
        PluginBaselineComparison: true,
        PressureScoreDetails: true,
        LogInspector: true,
        SegmentedControl: true,
      },
    },
  })
}

describe('PluginDetailModal', () => {
  beforeEach(() => {
    grants.clear()
    vi.mocked(diagnosticGet).mockReset().mockResolvedValue(detail)
  })

  it('queries only the target plugin and range, and refreshes after range changes', async () => {
    const wrapper = mountDetail()
    await flushPromises()
    expect(diagnosticGet).toHaveBeenLastCalledWith('plugin-detail', expect.any(AbortSignal), {
      pluginId: 'alpha',
      range: '1h',
    })
    expect(wrapper.text()).toContain('只覆盖归属线程')
    await wrapper.get('select[aria-label="插件诊断范围"]').setValue('15m')
    await flushPromises()
    expect(diagnosticGet).toHaveBeenLastCalledWith('plugin-detail', expect.any(AbortSignal), {
      pluginId: 'alpha',
      range: '15m',
    })
    wrapper.unmount()
  })

  it('keeps logs inaccessible without diagnostic permission, including a forged tab selection', async () => {
    const wrapper = mountDetail()
    await flushPromises()
    const control = wrapper.findComponent(SegmentedControl)
    expect(control.props('options')).not.toContainEqual({ label: '相关日志', value: 'logs' })
    control.vm.$emit('update:modelValue', 'logs')
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent(LogInspector).exists()).toBe(false)
    expect(diagnosticGet).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('passes the target plugin to logs when diagnostic permission is granted', async () => {
    grants.add('plugin:insight:diagnose')
    const wrapper = mountDetail()
    await flushPromises()
    wrapper.findComponent(SegmentedControl).vm.$emit('update:modelValue', 'logs')
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent(LogInspector).props('initialPluginId')).toBe('alpha')
    wrapper.unmount()
  })
})
