import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as insightApi from '@/api/insight'
import { usePerformanceResources } from '@/composables/insight/usePerformanceResources'
import { createPluginRankingItem } from '@/test/fixtures/pluginRanking'
import type { PluginRankingItem, TimeRange } from '@/types/insight'

vi.mock('@/api/insight', () => ({
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
})
