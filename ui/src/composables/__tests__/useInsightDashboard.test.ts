import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as insightApi from '@/api/insight'
import { useInsightDashboard } from '@/composables/useInsightDashboard'
import { createPluginRankingItem } from '@/test/fixtures/pluginRanking'
import type {
  AlertIncident,
  CursorPage,
  DashboardTab,
  MonitorEvent,
  OverviewResponse,
  PluginRankingItem,
  TimeRange,
  TrendsResponse,
} from '@/types/insight'

vi.mock('@halo-dev/components', () => ({
  Toast: {
    error: vi.fn<(message: string) => void>(),
    success: vi.fn<(message: string) => void>(),
  },
}))

vi.mock('@/api/insight', () => ({
  acknowledgeAlert: vi.fn<typeof insightApi.acknowledgeAlert>(),
  deleteData: vi.fn<typeof insightApi.deleteData>(),
  downloadExport: vi.fn<typeof insightApi.downloadExport>(),
  fetchAlerts: vi.fn<typeof insightApi.fetchAlerts>(),
  fetchAlertHistory: vi.fn<typeof insightApi.fetchAlertHistory>(),
  fetchEvents: vi.fn<typeof insightApi.fetchEvents>(),
  fetchOverview: vi.fn<typeof insightApi.fetchOverview>(),
  fetchPlugins: vi.fn<typeof insightApi.fetchPlugins>(),
  fetchRoutes: vi.fn<typeof insightApi.fetchRoutes>(),
  fetchTrends: vi.fn<typeof insightApi.fetchTrends>(),
  testNotifications: vi.fn<typeof insightApi.testNotifications>(),
}))

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function alert(id: string, status = 'OPEN'): AlertIncident {
  return {
    id,
    ruleId: `rule-${id}`,
    status,
    severity: 'warning',
    title: id,
    summary: id,
    fingerprint: id,
    triggeredAtMs: 1,
    updatedAtMs: 1,
    occurrenceCount: 1,
  }
}

function event(id: string, type = 'lifecycle'): MonitorEvent {
  return {
    id,
    occurredAtMs: 1,
    type,
    severity: 'info',
    source: 'test',
    title: id,
    summary: id,
    fingerprint: id,
    firstSeenAtMs: 1,
    lastSeenAtMs: 1,
    occurrenceCount: 1,
  }
}

function mountDashboard(tab: DashboardTab): {
  dashboard: ReturnType<typeof useInsightDashboard>
  wrapper: VueWrapper
} {
  const activeTab = ref<DashboardTab>(tab)
  const range = ref<TimeRange>('1h')
  let dashboard!: ReturnType<typeof useInsightDashboard>

  const wrapper = mount(
    defineComponent({
      setup() {
        dashboard = useInsightDashboard(activeTab, range)
        return () => h('div')
      },
    }),
  )

  return { dashboard, wrapper }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(insightApi.fetchOverview).mockResolvedValue({} as OverviewResponse)
  vi.mocked(insightApi.fetchTrends).mockResolvedValue({} as TrendsResponse)
  vi.mocked(insightApi.fetchPlugins).mockResolvedValue([])
  vi.mocked(insightApi.fetchRoutes).mockResolvedValue([])
  vi.mocked(insightApi.fetchAlerts).mockResolvedValue([])
  vi.mocked(insightApi.fetchAlertHistory).mockResolvedValue({ items: [], nextCursor: null })
  vi.mocked(insightApi.fetchEvents).mockResolvedValue({ items: [], nextCursor: null })
})

describe('useInsightDashboard request ordering', () => {
  it('keeps the newest plugin sort response when requests resolve out of order', async () => {
    const first = deferred<PluginRankingItem[]>()
    const second = deferred<PluginRankingItem[]>()
    vi.mocked(insightApi.fetchPlugins)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const { dashboard, wrapper } = mountDashboard('performance')
    expect(insightApi.fetchPlugins).toHaveBeenCalledTimes(1)

    dashboard.pluginSort.value = 'latency'
    await nextTick()

    expect(insightApi.fetchPlugins).toHaveBeenCalledTimes(2)
    expect(vi.mocked(insightApi.fetchPlugins).mock.calls[0]?.[2]?.aborted).toBe(true)

    second.resolve([createPluginRankingItem('new-sort')])
    await flushPromises()
    expect(dashboard.plugins.data.value?.[0]?.pluginId).toBe('new-sort')

    first.resolve([createPluginRankingItem('old-sort')])
    await flushPromises()
    expect(dashboard.plugins.data.value?.[0]?.pluginId).toBe('new-sort')

    wrapper.unmount()
  })

  it('keeps the newest alert status response and preserves cached data on failure', async () => {
    const first = deferred<{ items: AlertIncident[]; nextCursor: string | null }>()
    const second = deferred<{ items: AlertIncident[]; nextCursor: string | null }>()
    vi.mocked(insightApi.fetchAlertHistory)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const { dashboard, wrapper } = mountDashboard('alerts')
    dashboard.alertStatus.value = 'resolved'
    await nextTick()

    second.resolve({ items: [alert('resolved', 'RESOLVED')], nextCursor: null })
    await flushPromises()
    expect(dashboard.alerts.data.value?.[0]?.id).toBe('resolved')

    first.resolve({ items: [alert('stale')], nextCursor: null })
    await flushPromises()
    expect(dashboard.alerts.data.value?.[0]?.id).toBe('resolved')

    const failed = deferred<{ items: AlertIncident[]; nextCursor: string | null }>()
    vi.mocked(insightApi.fetchAlertHistory).mockReturnValueOnce(failed.promise)
    dashboard.alertStatus.value = 'all'
    await nextTick()
    failed.reject(new Error('refresh failed'))
    await flushPromises()

    expect(dashboard.alerts.data.value?.[0]?.id).toBe('resolved')
    expect(dashboard.alerts.error.value).toBe('refresh failed')

    wrapper.unmount()
  })

  it('does not append an old pagination response after event filters change', async () => {
    const initial = deferred<CursorPage<MonitorEvent>>()
    const oldPage = deferred<CursorPage<MonitorEvent>>()
    const filtered = deferred<CursorPage<MonitorEvent>>()
    vi.mocked(insightApi.fetchEvents)
      .mockReturnValueOnce(initial.promise)
      .mockReturnValueOnce(oldPage.promise)
      .mockReturnValueOnce(filtered.promise)

    const { dashboard, wrapper } = mountDashboard('events')
    initial.resolve({ items: [event('initial')], nextCursor: 'older' })
    await flushPromises()

    const loadMore = dashboard.loadMoreEvents()
    expect(insightApi.fetchEvents).toHaveBeenCalledTimes(2)

    dashboard.eventFilters.type = 'plugin'
    await nextTick()

    expect(insightApi.fetchEvents).toHaveBeenCalledTimes(3)
    expect(vi.mocked(insightApi.fetchEvents).mock.calls[1]?.[1]?.aborted).toBe(true)

    filtered.resolve({ items: [event('filtered', 'plugin')], nextCursor: null })
    await flushPromises()
    expect(dashboard.events.data.value?.items.map((item) => item.id)).toEqual(['filtered'])

    oldPage.resolve({ items: [event('old-page')], nextCursor: null })
    await loadMore
    expect(dashboard.events.data.value?.items.map((item) => item.id)).toEqual(['filtered'])

    wrapper.unmount()
  })
})
