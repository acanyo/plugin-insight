import { describe, expect, it } from 'vitest'

import type { AlertIncident } from '@/types/insight'
import { dependencyAlertDetails, dependencyProviderLabel } from '@/utils/alertDetails'

function incident(detailsJson: string | null): AlertIncident {
  return {
    id: 'incident-1',
    ruleId: 'halo.dependency',
    status: 'OPEN',
    severity: 'critical',
    title: 'Halo 依赖服务异常',
    summary: 'summary',
    fingerprint: 'insight-alert:halo.dependency',
    triggeredAtMs: 1,
    updatedAtMs: 1,
    occurrenceCount: 1,
    detailsJson,
  }
}

describe('dependencyAlertDetails', () => {
  it('reads dependency evidence and related plugin identity', () => {
    const details = dependencyAlertDetails(
      incident(
        JSON.stringify({
          dependencies: [
            {
              key: 'search',
              name: '搜索服务',
              status: 'warning',
              message: '搜索引擎暂不可用',
              providers: [{ pluginId: 'search-x', displayName: '搜索插件' }],
            },
          ],
        }),
      ),
    )

    expect(details).toHaveLength(1)
    expect(details[0]?.providers?.[0]?.pluginId).toBe('search-x')
    expect(dependencyProviderLabel(details[0]!.providers![0]!)).toBe('搜索插件（search-x）')
  })

  it('ignores malformed or legacy details', () => {
    expect(dependencyAlertDetails(incident('{broken'))).toEqual([])
    expect(dependencyAlertDetails(incident('{"metric":"halo.dependency.failed.count"}'))).toEqual(
      [],
    )
  })
})
