import { Toast } from '@halo-dev/components'
import { ref } from 'vue'

import { acknowledgeAlert as acknowledgeAlertRequest, fetchAlertHistory } from '@/api/insight'
import type { AlertIncident, AlertStatus } from '@/types/insight'
import { errorMessage } from '@/utils/format'
import { createLatestRequestChannel } from '@/utils/latestRequest'

import { createResource, isCanceledRequest, loadResource } from './resourceState'

export function useAlertsResource(onAcknowledged?: () => Promise<unknown>) {
  const alerts = createResource<AlertIncident[]>()
  const alertStatus = ref<AlertStatus>('open')
  const acknowledgingAlertIds = ref<Set<string>>(new Set())
  const requests = createLatestRequestChannel()
  const pageRequests = createLatestRequestChannel()
  const nextCursor = ref<string | null>(null)
  const loadingMore = ref(false)
  let queryGeneration = 0

  function refreshAlerts(requestedStatus = alertStatus.value, silent = false): Promise<boolean> {
    queryGeneration += 1
    pageRequests.abort()
    loadingMore.value = false
    nextCursor.value = null
    const request = requests.start()
    return loadResource<AlertIncident[]>(
      alerts,
      request,
      async (signal) => {
        const page = await fetchAlertHistory(requestedStatus, undefined, 50, signal)
        if (request.isCurrent()) nextCursor.value = page.nextCursor ?? null
        return page.items
      },
      silent,
    )
  }

  async function loadMoreAlerts(): Promise<boolean> {
    const cursor = nextCursor.value
    if (!cursor || loadingMore.value) return false
    const requestedGeneration = queryGeneration
    const requestedStatus = alertStatus.value
    const request = pageRequests.start()
    loadingMore.value = true
    alerts.error.value = undefined
    try {
      const page = await fetchAlertHistory(requestedStatus, cursor, 50, request.signal)
      if (!request.isCurrent() || requestedGeneration !== queryGeneration) return false
      const existing = alerts.data.value ?? []
      const known = new Set(existing.map((alert) => alert.id))
      alerts.data.value = existing.concat(page.items.filter((alert) => !known.has(alert.id)))
      nextCursor.value = page.nextCursor ?? null
      return true
    } catch (error) {
      if (request.isCurrent() && !isCanceledRequest(error)) {
        alerts.error.value = errorMessage(error)
      }
      return false
    } finally {
      if (request.isCurrent()) loadingMore.value = false
    }
  }

  async function acknowledgeAlert(id: string) {
    acknowledgingAlertIds.value = new Set(acknowledgingAlertIds.value).add(id)
    try {
      await acknowledgeAlertRequest(id)
      Toast.success('告警已确认')
      await Promise.all([refreshAlerts(), onAcknowledged?.()])
    } catch (error) {
      Toast.error(errorMessage(error))
    } finally {
      const next = new Set(acknowledgingAlertIds.value)
      next.delete(id)
      acknowledgingAlertIds.value = next
    }
  }

  function abortAlertRequests() {
    queryGeneration += 1
    requests.abort()
    pageRequests.abort()
    loadingMore.value = false
  }

  return {
    alerts,
    alertStatus,
    acknowledgingAlertIds,
    refreshAlerts,
    loadMoreAlerts,
    loadingMore,
    nextCursor,
    acknowledgeAlert,
    abortAlertRequests,
  }
}
