import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as insightApi from '@/api/insight'
import { useEventTimeline } from '@/composables/insight/useEventTimeline'
import type { CursorPage, MonitorEvent } from '@/types/insight'

vi.mock('@/api/insight', () => ({
  fetchEvents: vi.fn<typeof insightApi.fetchEvents>(),
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

function event(id: string): MonitorEvent {
  return {
    id,
    occurredAtMs: 1,
    type: 'lifecycle',
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

beforeEach(() => {
  vi.resetAllMocks()
})

describe('useEventTimeline', () => {
  it('does not append an obsolete page after the query changes', async () => {
    vi.mocked(insightApi.fetchEvents).mockResolvedValueOnce({
      items: [event('initial')],
      nextCursor: 'older',
    })
    const oldPage = deferred<CursorPage<MonitorEvent>>()
    const filteredPage = deferred<CursorPage<MonitorEvent>>()
    vi.mocked(insightApi.fetchEvents)
      .mockReturnValueOnce(oldPage.promise)
      .mockReturnValueOnce(filteredPage.promise)

    const timeline = useEventTimeline()
    await timeline.resetEvents()
    const loadMore = timeline.loadMoreEvents()

    timeline.eventFilters.type = 'plugin'
    const reset = timeline.resetEvents()
    expect(vi.mocked(insightApi.fetchEvents).mock.calls[1]?.[1]?.aborted).toBe(true)

    filteredPage.resolve({ items: [event('filtered')], nextCursor: null })
    await reset
    oldPage.resolve({ items: [event('obsolete')], nextCursor: null })
    await loadMore

    expect(timeline.events.data.value?.items.map((item) => item.id)).toEqual(['filtered'])
  })
})
