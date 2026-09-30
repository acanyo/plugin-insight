import type { HealthStatus, OverviewResponse } from '@/types/insight'
import { healthStatus } from '@/utils/format'

export interface CollectorSignal {
  status: HealthStatus
  label: string
  message: string
}

const labels: Record<HealthStatus, string> = {
  healthy: '实时采集',
  warning: '采样延迟',
  critical: '采集异常',
  unknown: '等待采样',
}

export function resolveCollectorSignal(overview?: OverviewResponse): CollectorSignal {
  if (!overview) {
    return { status: 'unknown', label: labels.unknown, message: '等待第一批监控样本' }
  }

  if (overview.runtime.lastCollectionError) {
    return {
      status: 'critical',
      label: labels.critical,
      message: overview.runtime.lastCollectionError,
    }
  }

  const collector = overview.dependencies.find((item) => item.key === 'collector')
  const status = collector ? healthStatus(collector.status) : 'unknown'
  return {
    status,
    label: labels[status],
    message: collector?.message || '尚未收到采集任务状态',
  }
}
