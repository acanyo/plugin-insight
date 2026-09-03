import type { HealthStatus, Severity } from '@/types/insight'

const numberFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 })
const compactFormatter = new Intl.NumberFormat('zh-CN', {
  notation: 'compact',
  maximumFractionDigits: 1,
})

export function normalizePercent(value?: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.max(0, Math.min(100, value ?? 0))
}

export function formatPercent(value?: number, digits = 1): string {
  return `${normalizePercent(value).toFixed(digits)}%`
}

export function ratioToPercent(value?: number): number {
  if (!Number.isFinite(value)) return 0
  return normalizePercent((value ?? 0) * 100)
}

export function formatNumber(value?: number): string {
  if (!Number.isFinite(value)) return '—'
  return numberFormatter.format(value ?? 0)
}

export function formatCompactNumber(value?: number): string {
  if (!Number.isFinite(value)) return '—'
  return compactFormatter.format(value ?? 0)
}

export function formatBytes(value?: number): string {
  if (!Number.isFinite(value)) return '—'
  const bytes = Math.max(value ?? 0, 0)
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size >= 10 || unitIndex === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unitIndex]}`
}

export function formatLatency(value?: number): string {
  if (!Number.isFinite(value)) return '—'
  if ((value ?? 0) >= 1000) return `${((value ?? 0) / 1000).toFixed(2)} s`
  return `${Math.round(value ?? 0)} ms`
}

export function formatDurationMs(milliseconds?: number): string {
  if (!Number.isFinite(milliseconds)) return '—'
  const total = Math.max(0, Math.floor((milliseconds ?? 0) / 1000))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  return `${Math.max(minutes, 1)} 分钟`
}

export function formatDateTime(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

export function formatRelativeTime(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '暂无记录'
  const timestamp = new Date(value).getTime()
  if (Number.isNaN(timestamp)) return String(value)
  const seconds = Math.round((timestamp - Date.now()) / 1000)
  const formatter = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'auto' })
  if (Math.abs(seconds) < 60) return formatter.format(seconds, 'second')
  const minutes = Math.round(seconds / 60)
  if (Math.abs(minutes) < 60) return formatter.format(minutes, 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return formatter.format(hours, 'hour')
  return formatter.format(Math.round(hours / 24), 'day')
}

export function statusLabel(status: HealthStatus): string {
  return {
    healthy: '正常',
    warning: '需关注',
    critical: '异常',
    unknown: '未知',
  }[status]
}

export function healthStatus(value?: string): HealthStatus {
  const normalized = value?.toLowerCase()
  if (normalized === 'healthy' || normalized === 'warning' || normalized === 'critical') {
    return normalized
  }
  return 'unknown'
}

export function severityTone(value?: string): Severity {
  const normalized = value?.toLowerCase()
  if (normalized === 'critical' || normalized === 'warning') return normalized
  return 'info'
}

export function severityLabel(severity: Severity): string {
  return {
    info: '信息',
    warning: '警告',
    critical: '严重',
  }[severity]
}

export function errorMessage(error: unknown): string {
  if (typeof error === 'object' && error !== null) {
    const candidate = error as {
      message?: string
      response?: { data?: { message?: string } }
    }
    return candidate.response?.data?.message || candidate.message || '请求失败，请稍后重试。'
  }
  return '请求失败，请稍后重试。'
}
