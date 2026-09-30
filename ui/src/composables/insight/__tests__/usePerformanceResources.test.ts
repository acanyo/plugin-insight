import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as insightApi from '@/api/insight'
import { usePerformanceResources } from '@/composables/insight/usePerformanceResources'
import { createPluginRankingItem } from '@/test/fixtures/pluginRanking'
import type { PluginRankingItem, RequestRouteItem, TimeRange } from '@/types/insight'

vi.mock('@/api/insight', () => ({
  fetchConsumers: vi.fn<typeof insightApi.fetchConsumers>(),
  fetchPlugins: vi.fn<typeof insightApi.fetchPlugins>(),
  fetchRoutes: vi.fn<typeof insightApi.fetchRoutes>(),
  fetchTrends: vi.fn<typeof insightApi.fetchTrends>(),
}))

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(insightApi.fetchConsumers).mockResolvedValue([])
})

describe('usePerformanceResources', () => {
  it('keeps only the newest plugin ranking response', async () => {
    const first = deferred<PluginRankingItem[]>()
    const second = deferred<PluginRankingItem[]>()
    vi.mocked(insightApi.fetchPlugins)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const resources = usePerformanceResources(ref<TimeRange>('1h'))
    const oldRequest = resources.refreshPlugins()
    resources.pluginSort.value = 'latency'
    const newRequest = resources.refreshPlugins()

    expect(vi.mocked(insightApi.fetchPlugins).mock.calls[0]?.[2]?.aborted).toBe(true)

    second.resolve([createPluginRankingItem('new')])
    await newRequest
    expect(resources.plugins.data.value?.[0]?.pluginId).toBe('new')

    first.resolve([createPluginRankingItem('old')])
    await oldRequest
    expect(resources.plugins.data.value?.[0]?.pluginId).toBe('new')
  })

  it('requests routes with the selected ranking mode', async () => {
    vi.mocked(insightApi.fetchRoutes).mockResolvedValue([])
    const resources = usePerformanceResources(ref<TimeRange>('6h'))
    resources.routeSort.value = 'p99'

    await resources.refreshRoutes()

    expect(insightApi.fetchRoutes).toHaveBeenCalledWith('6h', 'p99', expect.any(AbortSignal))
  })

  it('keeps only the newest route ranking response', async () => {
    const first = deferred<RequestRouteItem[]>()
    const second = deferred<RequestRouteItem[]>()
    vi.mocked(insightApi.fetchRoutes)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const resources = usePerformanceResources(ref<TimeRange>('1h'))
    const oldRequest = resources.refreshRoutes()
    resources.routeSort.value = 'requests'
    const newRequest = resources.refreshRoutes()

    expect(vi.mocked(insightApi.fetchRoutes).mock.calls[0]?.[2]?.aborted).toBe(true)

    second.resolve([route('new')])
    await newRequest
    expect(resources.routes.data.value?.[0]?.route).toBe('new')

    first.resolve([route('old')])
    await oldRequest
    expect(resources.routes.data.value?.[0]?.route).toBe('new')
  })
})

function route(path: string): RequestRouteItem {
  return {
    scope: 'halo-core',
    route: path,
    method: 'GET',
    requestCount: 1,
    errorCount: 0,
    errorRatePercent: 0,
    averageLatencyMs: 10,
    p95Ms: 25,
    p99Ms: 50,
    maximumLatencyMs: 50,
  }
}
