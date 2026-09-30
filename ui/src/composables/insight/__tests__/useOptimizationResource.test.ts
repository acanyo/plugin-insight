import { Toast } from '@halo-dev/components'
import { describe, expect, it, vi } from 'vitest'

import * as insightApi from '@/api/insight'
import { useOptimizationResource } from '@/composables/insight/useOptimizationResource'
import type { OptimizationStatus } from '@/types/insight'

vi.mock('@halo-dev/components', () => ({
  Toast: {
    error: vi.fn<(message: string) => void>(),
    success: vi.fn<(message: string) => void>(),
  },
}))

vi.mock('@/api/insight', () => ({
  applyOptimization: vi.fn<typeof insightApi.applyOptimization>(),
  fetchOptimization: vi.fn<typeof insightApi.fetchOptimization>(),
  rollbackOptimization: vi.fn<typeof insightApi.rollbackOptimization>(),
}))

function status(enabled: boolean): OptimizationStatus {
  return {
    mode: enabled ? 'low-overhead' : 'standard',
    enabled,
    canApply: true,
    appliedAtMs: enabled ? 1 : 0,
    updatedAtMs: 1,
    configuredSamplingSeconds: 15,
    effectiveSamplingSeconds: enabled ? 30 : 15,
    optimizedSamplingSeconds: 30,
    configuredPluginThreadLimit: 512,
    effectivePluginThreadLimit: enabled ? 256 : 512,
    optimizedPluginThreadLimit: 256,
    pluginResourceSamplingEnabled: true,
    runtime: {
      level: 'healthy',
      summary: 'ok',
      heapUsedBytes: 50,
      heapMaxBytes: 100,
      heapUsedPercent: 50,
      processCpuPercent: 10,
      systemCpuPercent: 20,
      gcCount: 1,
      gcTimeMs: 2,
      gcOverheadPercent: 0.1,
      liveThreads: 20,
      deadlockedThreads: 0,
      findings: [],
    },
    changes: [],
  }
}

describe('useOptimizationResource', () => {
  it('applies and rolls back the reversible profile', async () => {
    const changed = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    vi.mocked(insightApi.applyOptimization).mockResolvedValue(status(true))
    vi.mocked(insightApi.rollbackOptimization).mockResolvedValue(status(false))
    const resource = useOptimizationResource(changed)

    await resource.applyOptimization()

    expect(resource.optimization.data.value?.enabled).toBe(true)
    expect(Toast.success).toHaveBeenCalledWith('已降低 Insight 自身监控开销')

    await resource.rollbackOptimization()

    expect(resource.optimization.data.value?.enabled).toBe(false)
    expect(changed).toHaveBeenCalledTimes(2)
  })
})
