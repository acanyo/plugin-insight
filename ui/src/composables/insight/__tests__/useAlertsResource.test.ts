import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as insightApi from '@/api/insight'
import { useAlertsResource } from '@/composables/insight/useAlertsResource'
import type { AlertIncident } from '@/types/insight'

vi.mock('@halo-dev/components', () => ({
  Toast: {
    error: vi.fn<(message: string) => void>(),
    success: vi.fn<(message: string) => void>(),
  },
}))

vi.mock('@/api/insight', () => ({
  acknowledgeAlert: vi.fn<typeof insightApi.acknowledgeAlert>(),
  fetchAlertHistory: vi.fn<typeof insightApi.fetchAlertHistory>(),
  fetchAlerts: vi.fn<typeof insightApi.fetchAlerts>(),
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

beforeEach(() => {
  vi.resetAllMocks()
})

describe('useAlertsResource', () => {
  it('retains cached incidents when the newest refresh fails', async () => {
    const resources = useAlertsResource()
    vi.mocked(insightApi.fetchAlertHistory).mockResolvedValueOnce({
      items: [alert('cached')],
      nextCursor: null,
    })
    await resources.refreshAlerts()

    const failed = deferred<{ items: AlertIncident[]; nextCursor: string | null }>()
    vi.mocked(insightApi.fetchAlertHistory).mockReturnValueOnce(failed.promise)
    const refresh = resources.refreshAlerts('all')
    failed.reject(new Error('refresh failed'))
    await refresh

    expect(resources.alerts.data.value?.[0]?.id).toBe('cached')
    expect(resources.alerts.error.value).toBe('refresh failed')
  })

  it('does not append a stale page after the alert status changes', async () => {
    vi.mocked(insightApi.fetchAlertHistory).mockResolvedValueOnce({
      items: [alert('open')],
      nextCursor: 'older',
    })
    const resources = useAlertsResource()
    await resources.refreshAlerts('open')

    const oldPage = deferred<{ items: AlertIncident[]; nextCursor: string | null }>()
    vi.mocked(insightApi.fetchAlertHistory).mockReturnValueOnce(oldPage.promise)
    const loadMore = resources.loadMoreAlerts()
    const oldSignal = vi.mocked(insightApi.fetchAlertHistory).mock.calls[1]?.[3]

    vi.mocked(insightApi.fetchAlertHistory).mockResolvedValueOnce({
      items: [alert('resolved', 'RESOLVED')],
      nextCursor: null,
    })
    resources.alertStatus.value = 'resolved'
    const refresh = resources.refreshAlerts('resolved')
    expect(oldSignal?.aborted).toBe(true)
    await refresh

    oldPage.resolve({ items: [alert('stale')], nextCursor: null })
    await loadMore

    expect(resources.alerts.data.value?.map((item) => item.id)).toEqual(['resolved'])
  })
})
