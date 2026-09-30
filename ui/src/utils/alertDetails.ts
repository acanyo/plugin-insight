import type { AlertIncident, DependencyProvider, DependencyStatus } from '@/types/insight'

interface AlertDetailsPayload {
  dependencies?: unknown
}

export function dependencyAlertDetails(alert: AlertIncident): DependencyStatus[] {
  if (alert.ruleId !== 'halo.dependency' || !alert.detailsJson) return []
  try {
    const payload = JSON.parse(alert.detailsJson) as AlertDetailsPayload
    if (!Array.isArray(payload.dependencies)) return []
    return payload.dependencies.filter(isDependencyStatus).map((dependency) => ({
      ...dependency,
      providers: Array.isArray(dependency.providers)
        ? dependency.providers.filter(isDependencyProvider)
        : [],
    }))
  } catch {
    return []
  }
}

export function dependencyProviderLabel(provider: DependencyProvider): string {
  return provider.displayName && provider.displayName !== provider.pluginId
    ? `${provider.displayName}（${provider.pluginId}）`
    : provider.pluginId
}

function isDependencyStatus(value: unknown): value is DependencyStatus {
  if (!value || typeof value !== 'object') return false
  const dependency = value as Record<string, unknown>
  return (
    typeof dependency.key === 'string' &&
    typeof dependency.name === 'string' &&
    typeof dependency.status === 'string' &&
    typeof dependency.message === 'string'
  )
}

function isDependencyProvider(value: unknown): value is DependencyProvider {
  if (!value || typeof value !== 'object') return false
  const provider = value as Record<string, unknown>
  return (
    typeof provider.pluginId === 'string' &&
    (provider.displayName == null || typeof provider.displayName === 'string')
  )
}
