import { computed, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

import type { DashboardTab, TimeRange } from '@/types/insight'

import { useAlertsResource } from './insight/useAlertsResource'
import { useEventTimeline } from './insight/useEventTimeline'
import { useInsightActions } from './insight/useInsightActions'
import { useOverviewResource } from './insight/useOverviewResource'
import { usePerformanceResources } from './insight/usePerformanceResources'
import { usePolling } from './usePolling'

export type { ResourceState } from './insight/resourceState'

export function useInsightDashboard(activeTab: Ref<DashboardTab>, range: Ref<TimeRange>) {
  const overviewResource = useOverviewResource()
  const performanceResources = usePerformanceResources(range)
  const alertsResource = useAlertsResource(() => overviewResource.refreshOverview(true))
  const eventTimeline = useEventTimeline()

  const lastUpdatedAt = ref<number>()
  const refreshing = ref(false)
  let refreshGeneration = 0

  const hasAnyData = computed(() =>
    Boolean(
      overviewResource.overview.data.value
      || performanceResources.trends.data.value
      || alertsResource.alerts.data.value,
    ),
  )

  async function refresh(silent = false) {
    const generation = ++refreshGeneration
    const requestedRange = range.value
    const requestedTab = activeTab.value
    const requestedSort = performanceResources.pluginSort.value
    const requestedAlertStatus = alertsResource.alertStatus.value
    if (!silent) refreshing.value = true

    const tasks: Promise<boolean>[] = [
      overviewResource.refreshOverview(silent),
      performanceResources.refreshTrends(requestedRange, silent),
      alertsResource.refreshAlerts(requestedAlertStatus, silent),
    ]

    if (requestedTab === 'performance') {
      tasks.push(
        performanceResources.refreshPerformance(requestedRange, requestedSort, silent),
      )
    }

    if (requestedTab === 'events') {
      tasks.push(eventTimeline.resetEvents(silent))
    } else {
      eventTimeline.cancelEventRequests()
    }

    const results = await Promise.all(tasks)
    if (generation !== refreshGeneration) return
    if (results.some(Boolean)) lastUpdatedAt.value = Date.now()
    refreshing.value = false
  }

  const actions = useInsightActions(range, () => refresh())

  usePolling(() => refresh(true), 30_000)

  onMounted(() => void refresh())
  onBeforeUnmount(() => {
    refreshGeneration += 1
    overviewResource.abortOverviewRequests()
    performanceResources.abortPerformanceRequests()
    alertsResource.abortAlertRequests()
    eventTimeline.cancelEventRequests()
  })

  watch([range, activeTab], () => void refresh())
  watch(performanceResources.pluginSort, () => {
    if (activeTab.value === 'performance') void performanceResources.refreshPlugins()
  })
  watch(alertsResource.alertStatus, () => void alertsResource.refreshAlerts())
  watch(
    () => [eventTimeline.eventFilters.type, eventTimeline.eventFilters.severity],
    () => {
      if (activeTab.value === 'events') void eventTimeline.resetEvents()
    },
  )

  return {
    overview: overviewResource.overview,
    trends: performanceResources.trends,
    plugins: performanceResources.plugins,
    routes: performanceResources.routes,
    alerts: alertsResource.alerts,
    events: eventTimeline.events,
    pluginSort: performanceResources.pluginSort,
    alertStatus: alertsResource.alertStatus,
    eventFilters: eventTimeline.eventFilters,
    lastUpdatedAt,
    refreshing,
    exporting: actions.exporting,
    testingNotifications: actions.testingNotifications,
    clearingData: actions.clearingData,
    loadingMoreEvents: eventTimeline.loadingMoreEvents,
    loadingMoreAlerts: alertsResource.loadingMore,
    hasMoreAlerts: computed(() => Boolean(alertsResource.nextCursor.value)),
    acknowledgingAlertIds: alertsResource.acknowledgingAlertIds,
    hasAnyData,
    refresh,
    loadMoreEvents: eventTimeline.loadMoreEvents,
    loadMoreAlerts: alertsResource.loadMoreAlerts,
    acknowledgeAlert: alertsResource.acknowledgeAlert,
    sendTestNotification: actions.sendTestNotification,
    clearMonitoringData: actions.clearMonitoringData,
    exportData: actions.exportData,
  }
}
