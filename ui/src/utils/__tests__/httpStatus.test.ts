import { describe, expect, it } from 'vitest'

import { httpStatusTone } from '@/utils/httpStatus'

describe('httpStatusTone', () => {
  it.each([
    ['2xx', 'success'],
    ['302', 'redirect'],
    ['4xx', 'client-error'],
    ['503', 'server-error'],
    ['0', 'server-error'],
    ['unknown', 'unknown'],
  ] as const)('maps %s to %s', (code, tone) => {
    expect(httpStatusTone(code)).toBe(tone)
  })
})
