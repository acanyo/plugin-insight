import { describe, expect, it } from 'vitest'

import { resolveRuntimeHealth } from '@/utils/overviewHealth'

describe('resolveRuntimeHealth', () => {
  it('uses the server-computed overview health for a running runtime', () => {
    expect(resolveRuntimeHealth('warning', true, 0)).toBe('warning')
    expect(resolveRuntimeHealth('critical', true, 0)).toBe('critical')
  })

  it('keeps immediate stopped and deadlock states critical', () => {
    expect(resolveRuntimeHealth('healthy', false, 0)).toBe('critical')
    expect(resolveRuntimeHealth('healthy', true, 1)).toBe('critical')
  })

  it('does not turn an unrecognized server status into healthy', () => {
    expect(resolveRuntimeHealth('unexpected', true, 0)).toBe('unknown')
  })
})
