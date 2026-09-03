import { describe, expect, it } from 'vitest'

import { resolveExportFileName } from '@/api/insight'

describe('resolveExportFileName', () => {
  it('decodes an RFC 5987 filename', () => {
    expect(
      resolveExportFileName(
        "attachment; filename*=UTF-8''halo-insight-%E7%9B%91%E6%8E%A7.json",
        '1h',
      ),
    ).toBe('halo-insight-监控.json')
  })

  it('uses a quoted plain filename', () => {
    expect(resolveExportFileName('attachment; filename="halo-insight-export.json"', '1h')).toBe(
      'halo-insight-export.json',
    )
  })

  it('falls back to a deterministic range and date based filename', () => {
    expect(resolveExportFileName(undefined, '24h', new Date('2026-09-02T00:00:00Z'))).toBe(
      'halo-insight-24h-2026-09-02.json',
    )
  })
})
