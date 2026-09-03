import { describe, expect, it } from 'vitest'

import { createLatestRequestChannel } from '@/utils/latestRequest'

describe('createLatestRequestChannel', () => {
  it('aborts and invalidates the previous request when a newer request starts', () => {
    const channel = createLatestRequestChannel()
    const first = channel.start()
    const second = channel.start()

    expect(first.signal.aborted).toBe(true)
    expect(first.isCurrent()).toBe(false)
    expect(second.signal.aborted).toBe(false)
    expect(second.isCurrent()).toBe(true)
  })

  it('invalidates the current request when the channel is aborted', () => {
    const channel = createLatestRequestChannel()
    const request = channel.start()

    channel.abort()

    expect(request.signal.aborted).toBe(true)
    expect(request.isCurrent()).toBe(false)
  })
})
