import { describe, expect, it } from 'vitest'

import type { AlertIncident } from '@/types/insight'
import { isAcknowledgedAlert, isPendingAlert, summarizeAlerts } from '@/utils/alertStatus'

function alert(status: string, severity = 'warning'): AlertIncident {
  return {
    id: `${status}-${severity}`,
    ruleId: 'test.rule',
    status,
    severity,
    title: '测试告警',
    summary: '测试告警摘要',
    fingerprint: 'test',
    triggeredAtMs: 1,
    updatedAtMs: 1,
    occurrenceCount: 1,
  }
}

describe('alertStatus', () => {
  it('separates pending alerts from acknowledged alerts', () => {
    const pending = alert('OPEN', 'critical')
    const acknowledged = alert('acknowledged')
    const resolved = alert('RESOLVED', 'critical')

    expect(isPendingAlert(pending)).toBe(true)
    expect(isAcknowledgedAlert(acknowledged)).toBe(true)
    expect(summarizeAlerts([pending, acknowledged, resolved])).toMatchObject({
      pending: [pending],
      acknowledged: [acknowledged],
      active: [pending, acknowledged],
      pendingCritical: 1,
      pendingWarning: 0,
      activeCritical: 1,
    })
  })
})
