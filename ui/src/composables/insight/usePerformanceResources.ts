import { ref, type Ref } from 'vue'

import { fetchConsumers, fetchPlugins, fetchRoutes, fetchTrends } from '@/api/insight'
import type {
  PluginConsumerItem,
  PluginRankingItem,
  PluginSort,
  RequestRouteItem,
  RequestRouteSort,
  TimeRange,
  TrendsResponse,
} from '@/types/insight'
import { createLatestRequestChannel } from '@/utils/latestRequest'

import { createResource, loadLatestResource } from './resourceState'

export function usePerformanceResources(range: Ref<TimeRange>) {
  const trends = createResource<TrendsResponse>()
  const plugins = createResource<PluginRankingItem[]>()
  const consumers = createResource<PluginConsumerItem[]>()
  const routes = createResource<RequestRouteItem[]>()
  const pluginSort = ref<PluginSort>('load')
  const routeSort = ref<RequestRouteSort>('slow')

  const trendRequests = createLatestRequestChannel()
  const pluginRequests = createLatestRequestChannel()
  const consumerRequests = createLatestRequestChannel()
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
    requestedPluginSort = pluginSort.value,
    requestedRouteSort = routeSort.value,
    silent = false,
  ): Promise<boolean> {
    const results = await Promise.all([
      loadLatestResource(
        plugins,
        pluginRequests,
        (signal) => fetchPlugins(requestedRange, requestedPluginSort, signal),
        silent,
      ),
      loadLatestResource(
        consumers,
        consumerRequests,
        (signal) => fetchConsumers(requestedRange, signal),
        silent,
      ),
      loadLatestResource(
        routes,
        routeRequests,
        (signal) => fetchRoutes(requestedRange, requestedRouteSort, signal),
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

  function refreshRoutes(): Promise<boolean> {
    const requestedRange = range.value
    const requestedSort = routeSort.value
    return loadLatestResource(routes, routeRequests, (signal) =>
      fetchRoutes(requestedRange, requestedSort, signal),
    )
  }

  function abortPerformanceRequests() {
    trendRequests.abort()
    pluginRequests.abort()
    consumerRequests.abort()
    routeRequests.abort()
  }

  return {
    trends,
    plugins,
    consumers,
    routes,
    pluginSort,
    routeSort,
    refreshTrends,
    refreshPerformance,
    refreshPlugins,
    refreshRoutes,
    abortPerformanceRequests,
  }
}
