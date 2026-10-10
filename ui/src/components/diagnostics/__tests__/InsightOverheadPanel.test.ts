import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { diagnosticGet } from '@/api/diagnostics'
import InsightOverheadPanel from '@/components/diagnostics/InsightOverheadPanel.vue'
import type { OverheadSnapshot } from '@/types/diagnostics'

vi.mock('@/api/diagnostics', () => ({ diagnosticGet: vi.fn<typeof diagnosticGet>() }))
vi.mock('@halo-dev/components', () => ({
  VButton: { template: '<button type="button"><slot /></button>' },
  VAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
}))
describe('InsightOverheadPanel', () => {
  afterEach(() => vi.useRealTimers())
  it('shows unavailable counters explicitly and reads again only after manual refresh', async () => {
    vi.useFakeTimers()
    const data: OverheadSnapshot = {
      fromMs: 0,
      toMs: 60_000,
      cpuCorePercent: 0,
      allocatedBytesPerMinute: 0,
      cpuAvailable: false,
      allocationAvailable: false,
      databaseReads: 3,
      databaseWrites: 1,
      operations: [],
      points: [],
      coverage: '进程重启后重新累计',
    }
    vi.mocked(diagnosticGet).mockReset().mockResolvedValue(data)
    const wrapper = mount(InsightOverheadPanel)
    await flushPromises()
    expect(wrapper.text()).toContain('不可用')
    expect(wrapper.text()).not.toContain('0.000%')
    expect(wrapper.text()).toMatch(/SQLite 读取\s*3/)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(diagnosticGet).toHaveBeenCalledTimes(1)
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(diagnosticGet).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
