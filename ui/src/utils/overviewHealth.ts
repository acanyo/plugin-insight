import type { HealthStatus } from '@/types/insight'
import { healthStatus } from '@/utils/format'

/**
 * Resolves the runtime rail status from the server-computed overview health while
 * preserving failures that the currently rendered snapshot can detect immediately.
 */
export function resolveRuntimeHealth(
  overviewHealth: string | undefined,
  running: boolean,
  deadlockedThreads: number,
): HealthStatus {
  if (!running || deadlockedThreads > 0) return 'critical'
  return healthStatus(overviewHealth)
}
