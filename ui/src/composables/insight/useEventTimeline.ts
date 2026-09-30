import { reactive, ref } from 'vue'

import { fetchEvents } from '@/api/insight'
import type { CursorPage, EventQuery, MonitorEvent, Severity } from '@/types/insight'
import { errorMessage } from '@/utils/format'
import { createLatestRequestChannel } from '@/utils/latestRequest'

import { createResource, isCanceledRequest, loadResource } from './resourceState'

export function useEventTimeline() {
  const events = createResource<CursorPage<MonitorEvent>>()
  const eventFilters = reactive<{ type: string; severity: Severity | 'all' }>({
    type: 'all',
    severity: 'all',
  })
  const loadingMoreEvents = ref(false)

  const resetRequests = createLatestRequestChannel()
  const pageRequests = createLatestRequestChannel()
  let queryGeneration = 0
  let resetInFlight = false

  function currentQuery(cursor?: string): EventQuery {
    return {
      cursor,
      limit: 50,
      type: eventFilters.type === 'all' ? undefined : eventFilters.type,
      severity: eventFilters.severity === 'all' ? undefined : eventFilters.severity,
    }
  }

  function cancelEventRequests() {
    queryGeneration += 1
    resetRequests.abort()
    pageRequests.abort()
    resetInFlight = false
    loadingMoreEvents.value = false
    events.loading.value = false
  }

  async function resetEvents(silent = false): Promise<boolean> {
    queryGeneration += 1
    pageRequests.abort()
    loadingMoreEvents.value = false

    const query = currentQuery()
    const request = resetRequests.start()
    resetInFlight = true
    try {
      return await loadResource(events, request, (signal) => fetchEvents(query, signal), silent)
    } finally {
      if (request.isCurrent()) resetInFlight = false
    }
  }

  async function loadMoreEvents(): Promise<boolean> {
    const cursor = events.data.value?.nextCursor
    if (!cursor || loadingMoreEvents.value || resetInFlight) return false

    const requestedGeneration = queryGeneration
    const query = currentQuery(cursor)
    const request = pageRequests.start()
    loadingMoreEvents.value = true
    events.error.value = undefined

    try {
      const nextPage = await fetchEvents(query, request.signal)
      if (!request.isCurrent() || requestedGeneration !== queryGeneration) return false

      events.data.value = {
        items: [...(events.data.value?.items ?? []), ...nextPage.items],
        nextCursor: nextPage.nextCursor,
      }
      return true
    } catch (error) {
      if (request.isCurrent() && !isCanceledRequest(error)) {
        events.error.value = errorMessage(error)
      }
      return false
    } finally {
      if (request.isCurrent()) loadingMoreEvents.value = false
    }
  }

  return {
    events,
    eventFilters,
    loadingMoreEvents,
    resetEvents,
    loadMoreEvents,
    cancelEventRequests,
  }
}
