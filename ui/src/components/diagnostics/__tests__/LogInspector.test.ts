import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { diagnosticGet } from '@/api/diagnostics'
import LogInspector from '@/components/diagnostics/LogInspector.vue'
import type { LogResult } from '@/types/diagnostics'

vi.mock('@/api/diagnostics', () => ({ diagnosticGet: vi.fn<typeof diagnosticGet>() }))
vi.mock('@halo-dev/components', () => ({
  VButton: { template: '<button type="button"><slot /></button>' },
  VAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
}))

function result(params: Record<string, string | number> = {}): LogResult {
  const pluginId = String(params.pluginId ?? '')
  const page = Number(params.page ?? 1)
  return {
    snapshotId: 'snapshot-1',
    capturedAtMs: 1_800_000_000_000,
    available: true,
    sources: ['halo.log'],
    entries: [
      {
        level: 'ERROR',
        text: 'failed\njava.lang.IllegalStateException: unavailable',
        loggerName: 'com.example.Worker',
        pluginIds: [pluginId || 'alpha'],
        attribution: 'logger',
      },
    ],
    exceptions: [{ signature: 'java.lang.IllegalStateException: unavailable', count: 2 }],
    truncated: false,
    page,
    pageSize: 20,
    totalEntries: 45,
    hasNext: page < 3,
    plugins: [
      { pluginId: 'alpha', displayName: '甲插件', indexed: true },
      { pluginId: 'beta', displayName: '乙插件', indexed: true },
      { pluginId: 'missing', displayName: '未加载插件', indexed: false },
    ],
    pluginId,
    unattributedEntries: 3,
    message: '异常聚合覆盖当前筛选结果。',
  }
}

function lastRequest() {
  const calls = vi.mocked(diagnosticGet).mock.calls
  return calls[calls.length - 1]?.[2]
}

describe('LogInspector', () => {
  beforeEach(() => {
    vi.mocked(diagnosticGet).mockReset()
    vi.mocked(diagnosticGet).mockImplementation(async (_path, _signal, params) => result(params))
  })
  afterEach(() => vi.useRealTimers())

  it('loads plugin choices and refreshes the selected plugin from the first page', async () => {
    const wrapper = mount(LogInspector)
    await flushPromises()
    expect(lastRequest()).toMatchObject({ pluginId: '', page: 1 })
    const selector = wrapper.get('select[aria-label="日志插件"]')
    expect(selector.text()).toContain('甲插件（alpha）')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(lastRequest()).toMatchObject({ page: 2 })

    await selector.setValue('beta')
    await flushPromises()
    expect(lastRequest()).toMatchObject({ pluginId: 'beta', page: 1 })
    expect(wrapper.get('.diagnostic-record').text()).toMatch(/插件日志\s*·\s*乙插件/)
    expect(wrapper.text()).toContain('3 条日志无法识别插件归属')
    expect(wrapper.text()).toContain('异常堆栈聚合 · 1 组')
    wrapper.unmount()
  })

  it('combines level and keyword filters with the chosen plugin', async () => {
    const wrapper = mount(LogInspector)
    await flushPromises()
    await wrapper.get('select[aria-label="日志插件"]').setValue('alpha')
    await flushPromises()
    await wrapper.findAll('select')[2]!.setValue('ERROR')
    await flushPromises()
    await wrapper.get('input').setValue('IllegalStateException')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(lastRequest()).toMatchObject({
      pluginId: 'alpha',
      level: 'ERROR',
      keyword: 'IllegalStateException',
      page: 1,
    })
    await wrapper.get('select[aria-label="日志插件"]').setValue('')
    await flushPromises()
    expect(lastRequest()).toMatchObject({ pluginId: '', level: 'ERROR' })
    wrapper.unmount()
  })

  it('explains missing class indexes and does not poll logs in the background', async () => {
    vi.useFakeTimers()
    const wrapper = mount(LogInspector)
    await flushPromises()
    await wrapper.get('select[aria-label="日志插件"]').setValue('missing')
    await flushPromises()
    expect(wrapper.text()).toContain('当前插件的类索引不可用或没有可识别的类')
    const calls = vi.mocked(diagnosticGet).mock.calls.length
    await vi.advanceTimersByTimeAsync(60_000)
    expect(diagnosticGet).toHaveBeenCalledTimes(calls)
    wrapper.unmount()
  })

  it('uses a frozen snapshot for pages and reads incrementally without applying an edited keyword', async () => {
    const wrapper = mount(LogInspector, { props: { initialPluginId: 'alpha' } })
    await flushPromises()
    expect(lastRequest()).toMatchObject({ pluginId: 'alpha', mode: 'query', snapshotId: '' })
    await wrapper.get('input').setValue('not-submitted')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(lastRequest()).toMatchObject({
      page: 2,
      mode: 'page',
      snapshotId: 'snapshot-1',
      keyword: '',
    })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '读取新增日志')!
      .trigger('click')
    await flushPromises()
    expect(lastRequest()).toMatchObject({
      page: 1,
      mode: 'incremental',
      snapshotId: 'snapshot-1',
      keyword: '',
    })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(lastRequest()).toMatchObject({ mode: 'query', snapshotId: '', keyword: 'not-submitted' })
    wrapper.unmount()
  })

  it('retains displayed entries when a snapshot expires and allows a fresh query', async () => {
    const wrapper = mount(LogInspector)
    await flushPromises()
    vi.mocked(diagnosticGet).mockRejectedValueOnce(new Error('日志查询快照已过期，请重新查询'))
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '下一页')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('快照已过期')
    expect(wrapper.get('.diagnostic-record').text()).toContain('IllegalStateException')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(lastRequest()).toMatchObject({ mode: 'query', snapshotId: '', page: 1 })
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
