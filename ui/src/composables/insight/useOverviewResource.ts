import { fetchOverview } from '@/api/insight'
import type { OverviewResponse } from '@/types/insight'
import { createLatestRequestChannel } from '@/utils/latestRequest'

import { createResource, loadLatestResource } from './resourceState'

export function useOverviewResource() {
  const overview = createResource<OverviewResponse>()
  const requests = createLatestRequestChannel()

  function refreshOverview(silent = false): Promise<boolean> {
    return loadLatestResource(overview, requests, fetchOverview, silent)
  }

  function abortOverviewRequests() {
    requests.abort()
  }

  return {
    overview,
    refreshOverview,
    abortOverviewRequests,
  }
}
