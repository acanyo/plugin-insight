import { axiosInstance } from '@halo-dev/api-client'

import type {
  AlertIncident,
  AlertPage,
  AlertStatus,
  ClearDataResponse,
  CursorPage,
  EventQuery,
  MonitorEvent,
  NotificationResult,
  OptimizationStatus,
  OverviewResponse,
  PluginConsumerItem,
  PluginRankingItem,
  PluginSort,
  RequestRouteItem,
  RequestRouteSort,
  TimeRange,
  TrendsResponse,
} from '@/types/insight'

const API_BASE = '/apis/console.api.insight.halo.run/v1alpha1'
const READ_TIMEOUT_MS = 15_000
const COMMAND_TIMEOUT_MS = 30_000
const EXPORT_TIMEOUT_MS = 120_000

function readRequest(signal?: AbortSignal) {
  return { signal, timeout: READ_TIMEOUT_MS }
}

export async function fetchOverview(signal?: AbortSignal): Promise<OverviewResponse> {
  const { data } = await axiosInstance.get<OverviewResponse>(
    `${API_BASE}/overview`,
    readRequest(signal),
  )
  return data
}

export async function fetchTrends(range: TimeRange, signal?: AbortSignal): Promise<TrendsResponse> {
  const { data } = await axiosInstance.get<TrendsResponse>(`${API_BASE}/trends`, {
    params: { range },
    ...readRequest(signal),
  })
  return data
}

export async function fetchPlugins(
  range: TimeRange,
  sort: PluginSort,
  signal?: AbortSignal,
): Promise<PluginRankingItem[]> {
  const { data } = await axiosInstance.get<PluginRankingItem[]>(`${API_BASE}/plugins`, {
    params: { range, sort },
    ...readRequest(signal),
  })
  return data
}

export async function fetchConsumers(
  range: TimeRange,
  signal?: AbortSignal,
): Promise<PluginConsumerItem[]> {
  const { data } = await axiosInstance.get<PluginConsumerItem[]>(`${API_BASE}/consumers`, {
    params: { range },
    ...readRequest(signal),
  })
  return data
}

export async function fetchOptimization(signal?: AbortSignal): Promise<OptimizationStatus> {
  const { data } = await axiosInstance.get<OptimizationStatus>(
    `${API_BASE}/optimization`,
    readRequest(signal),
  )
  return data
}

export async function applyOptimization(): Promise<OptimizationStatus> {
  const { data } = await axiosInstance.post<OptimizationStatus>(
    `${API_BASE}/optimization/apply`,
    undefined,
    { timeout: COMMAND_TIMEOUT_MS },
  )
  return data
}

export async function rollbackOptimization(): Promise<OptimizationStatus> {
  const { data } = await axiosInstance.post<OptimizationStatus>(
    `${API_BASE}/optimization/rollback`,
    undefined,
    { timeout: COMMAND_TIMEOUT_MS },
  )
  return data
}

export async function fetchRoutes(
  range: TimeRange,
  sort: RequestRouteSort,
  signal?: AbortSignal,
): Promise<RequestRouteItem[]> {
  const { data } = await axiosInstance.get<RequestRouteItem[]>(`${API_BASE}/routes`, {
    params: { range, sort },
    ...readRequest(signal),
  })
  return data
}

export async function fetchEvents(
  query: EventQuery,
  signal?: AbortSignal,
): Promise<CursorPage<MonitorEvent>> {
  const { data } = await axiosInstance.get<CursorPage<MonitorEvent>>(`${API_BASE}/events`, {
    params: query,
    ...readRequest(signal),
  })
  return data
}

export async function fetchAlerts(
  status: AlertStatus,
  limit = 50,
  signal?: AbortSignal,
): Promise<AlertIncident[]> {
  const { data } = await axiosInstance.get<AlertIncident[]>(`${API_BASE}/alerts`, {
    params: { status, limit },
    ...readRequest(signal),
  })
  return data
}

export async function fetchAlertHistory(
  status: AlertStatus,
  cursor?: string,
  limit = 50,
  signal?: AbortSignal,
): Promise<AlertPage> {
  const { data } = await axiosInstance.get<AlertPage>(`${API_BASE}/alerts/history`, {
    params: { status, cursor, limit },
    ...readRequest(signal),
  })
  return data
}

export async function acknowledgeAlert(id: string): Promise<AlertIncident> {
  const { data } = await axiosInstance.post<AlertIncident>(
    `${API_BASE}/alerts/${encodeURIComponent(id)}/acknowledge`,
    undefined,
    { timeout: COMMAND_TIMEOUT_MS },
  )
  return data
}

export async function testNotifications(): Promise<NotificationResult[]> {
  const { data } = await axiosInstance.post<NotificationResult[]>(
    `${API_BASE}/notifications`,
    undefined,
    { timeout: COMMAND_TIMEOUT_MS },
  )
  return data
}

export async function deleteData(scope: string): Promise<ClearDataResponse> {
  const { data } = await axiosInstance.delete<ClearDataResponse>(`${API_BASE}/data`, {
    params: { scope },
    timeout: COMMAND_TIMEOUT_MS,
  })
  return data
}

function unquoteFileName(value: string): string {
  const trimmed = value.trim()
  return trimmed.startsWith('"') && trimmed.endsWith('"') ? trimmed.slice(1, -1) : trimmed
}

function decodeFileName(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

export function resolveExportFileName(
  contentDisposition: string | undefined,
  range: TimeRange,
  now = new Date(),
): string {
  const extended = contentDisposition?.match(/filename\*\s*=\s*(?:UTF-8'')?("[^"]*"|[^;]+)/i)?.[1]
  if (extended) return decodeFileName(unquoteFileName(extended))

  const plain = contentDisposition?.match(/(?:^|;)\s*filename\s*=\s*("[^"]*"|[^;]+)/i)?.[1]
  if (plain) return unquoteFileName(plain)

  return `halo-insight-${range}-${now.toISOString().slice(0, 10)}.json`
}

export async function downloadExport(range: TimeRange): Promise<void> {
  const response = await axiosInstance.get<Blob>(`${API_BASE}/export`, {
    params: { range },
    responseType: 'blob',
    timeout: EXPORT_TIMEOUT_MS,
  })
  const disposition = response.headers['content-disposition']
  const fileName = resolveExportFileName(
    typeof disposition === 'string' ? disposition : undefined,
    range,
  )
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
