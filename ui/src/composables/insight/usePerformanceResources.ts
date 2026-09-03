import { ref, type Ref } from 'vue'

import { fetchPlugins, fetchRoutes, fetchTrends } from '@/api/insight'
import type {
  PluginRankingItem,
  PluginSort,
  RequestRouteItem,
  TimeRange,
  TrendsResponse,
} from '@/types/insight'
import { createLatestRequestChannel } from '@/utils/latestRequest'

import { createResource, loadLatestResource } from './resourceState'

export function usePerformanceResources(range: Ref<TimeRange>) {
  const trends = createResource<TrendsResponse>()
  const plugins = createResource<PluginRankingItem[]>()
  const routes = createResource<RequestRouteItem[]>()
  const pluginSort = ref<PluginSort>('load')

  const trendRequests = createLatestRequestChannel()
  const pluginRequests = createLatestRequestChannel()
  const routeRequests = createLatestRequestChannel()

  function refreshTrends(requestedRange = range.value, silent = false): Promise<boolean> {
    return loadLatestResource(
      trends,
      trendRequests,
      (signal) => fetchTrends(requestedRange, signal),
      silent,
    )
  }

  async function refreshPerformance(
    requestedRange = range.value,
    requestedSort = pluginSort.value,
    silent = false,
  ): Promise<boolean> {
    const results = await Promise.all([
      loadLatestResource(
        plugins,
        pluginRequests,
        (signal) => fetchPlugins(requestedRange, requestedSort, signal),
        silent,
      ),
      loadLatestResource(
        routes,
        routeRequests,
        (signal) => fetchRoutes(requestedRange, signal),
        silent,
      ),
    ])
    return results.some(Boolean)
  }

  function refreshPlugins(): Promise<boolean> {
    const requestedRange = range.value
    const requestedSort = pluginSort.value
    return loadLatestResource(plugins, pluginRequests, (signal) =>
      fetchPlugins(requestedRange, requestedSort, signal),
    )
  }

  function abortPerformanceRequests() {
    trendRequests.abort()
    pluginRequests.abort()
    routeRequests.abort()
  }

  return {
    trends,
    plugins,
    routes,
    pluginSort,
    refreshTrends,
    refreshPerformance,
    refreshPlugins,
    abortPerformanceRequests,
  }
}
