import type { AlertIncident } from '@/types/insight'

export interface AlertSummary {
  pending: AlertIncident[]
  acknowledged: AlertIncident[]
  active: AlertIncident[]
  pendingCritical: number
  pendingWarning: number
  activeCritical: number
}

function normalizedStatus(alert: AlertIncident): string {
  return alert.status.toUpperCase()
}

function normalizedSeverity(alert: AlertIncident): string {
  return alert.severity.toLowerCase()
}

export function isPendingAlert(alert: AlertIncident): boolean {
  return normalizedStatus(alert) === 'OPEN'
}

export function isAcknowledgedAlert(alert: AlertIncident): boolean {
  return normalizedStatus(alert) === 'ACKNOWLEDGED'
}

export function summarizeAlerts(alerts: AlertIncident[]): AlertSummary {
  const pending = alerts.filter(isPendingAlert)
  const acknowledged = alerts.filter(isAcknowledgedAlert)
  const active = [...pending, ...acknowledged]

  return {
    pending,
    acknowledged,
    active,
    pendingCritical: pending.filter((alert) => normalizedSeverity(alert) === 'critical').length,
    pendingWarning: pending.filter((alert) => normalizedSeverity(alert) === 'warning').length,
    activeCritical: active.filter((alert) => normalizedSeverity(alert) === 'critical').length,
  }
}
