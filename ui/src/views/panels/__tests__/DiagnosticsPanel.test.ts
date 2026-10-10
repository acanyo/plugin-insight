import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DiagnosticsPanel from '@/views/panels/DiagnosticsPanel.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'

const { grants } = vi.hoisted(() => ({ grants: new Set<string>() }))
vi.mock('@halo-dev/components', () => ({
  VButton: { template: '<button><slot /></button>' },
  VAlert: { template: '<div><slot /></div>' },
  VLoading: { template: '<span />' },
  Toast: {
    success: vi.fn<(...args: unknown[]) => void>(),
    error: vi.fn<(...args: unknown[]) => void>(),
  },
  Dialog: { warning: vi.fn<(...args: unknown[]) => void>() },
}))
vi.mock('@halo-dev/ui-shared', () => ({
  utils: {
    permission: { has: (permissions: string[]) => permissions.every((p) => grants.has(p)) },
  },
}))

describe('DiagnosticsPanel permissions', () => {
  beforeEach(() => grants.clear())

  it('does not mount protected panels even when an unauthorized selection is emitted', async () => {
    const wrapper = shallowMount(DiagnosticsPanel)
    const control = wrapper.findComponent(SegmentedControl)
    expect(control.props('options').map((option: { value: string }) => option.value)).toEqual([
      'memory',
      'overhead',
      'observations',
      'tasks',
      'external',
    ])
    for (const selection of ['threads', 'logs', 'jfr', 'deliveries']) {
      control.vm.$emit('update:modelValue', selection)
      await wrapper.vm.$nextTick()
      expect(wrapper.find('thread-inspector-stub').exists()).toBe(false)
      expect(wrapper.find('log-inspector-stub').exists()).toBe(false)
      expect(wrapper.find('jfr-inspector-stub').exists()).toBe(false)
      expect(wrapper.find('delivery-history-stub').exists()).toBe(false)
    }
    wrapper.unmount()
  })

  it('gives managers delivery history while reserving logs and recordings for diagnostics', () => {
    grants.add('plugin:insight:manage')
    const wrapper = shallowMount(DiagnosticsPanel)
    const values = wrapper
      .findComponent(SegmentedControl)
      .props('options')
      .map((option: { value: string }) => option.value)
    expect(values).toContain('deliveries')
    expect(values).not.toContain('logs')
    expect(values).not.toContain('threads')
    expect(values).not.toContain('jfr')
    wrapper.unmount()
  })
})
