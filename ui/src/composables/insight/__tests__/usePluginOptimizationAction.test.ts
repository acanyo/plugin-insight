import { Toast } from '@halo-dev/components'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as pluginManagement from '@/api/pluginManagement'
import * as diagnostics from '@/api/diagnostics'
import type { Observation } from '@/types/diagnostics'
import { usePluginOptimizationAction } from '@/composables/insight/usePluginOptimizationAction'

vi.mock('@halo-dev/components', () => ({
  Toast: {
    error: vi.fn<(message: string) => void>(),
    success: vi.fn<(message: string) => void>(),
    warning: vi.fn<(message: string) => void>(),
  },
}))

vi.mock('@/api/diagnostics', () => ({
  prepareObservation: vi.fn<typeof diagnostics.prepareObservation>(),
  observationAction: vi.fn<typeof diagnostics.observationAction>(),
}))

vi.mock('@/api/pluginManagement', () => ({
  disablePlugin: vi.fn<typeof pluginManagement.disablePlugin>(),
}))

describe('usePluginOptimizationAction', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(diagnostics.prepareObservation).mockResolvedValue({
      id: 'observation-1',
    } as Observation)
    vi.mocked(diagnostics.observationAction).mockResolvedValue({
      id: 'observation-1',
    } as Observation)
  })

  it('disables the selected plugin and refreshes monitoring data', async () => {
    const changed = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    vi.mocked(pluginManagement.disablePlugin).mockResolvedValue(undefined)
    const action = usePluginOptimizationAction(changed)

    await action.disablePlugin('plugin-search', '搜索插件')

    expect(pluginManagement.disablePlugin).toHaveBeenCalledWith('plugin-search')
    expect(diagnostics.observationAction).toHaveBeenCalledWith('observation-1', 'confirm')
    expect(Toast.success).toHaveBeenCalledWith(
      '已停用 搜索插件，将自动对比前后 5 分钟指标，可在诊断中查看',
    )
    expect(changed).toHaveBeenCalledOnce()
  })

  it('refuses to disable Insight itself', async () => {
    const action = usePluginOptimizationAction(vi.fn<() => Promise<void>>())

    await action.disablePlugin('insight', 'Insight')

    expect(pluginManagement.disablePlugin).not.toHaveBeenCalled()
    expect(Toast.error).toHaveBeenCalledWith('Insight 不能停用自身来执行诊断。')
  })

  it('does not disable a plugin when the baseline could not be persisted', async () => {
    vi.mocked(diagnostics.prepareObservation).mockRejectedValue(new Error('storage unavailable'))
    await usePluginOptimizationAction(vi.fn<() => Promise<void>>()).disablePlugin('search', '搜索')
    expect(pluginManagement.disablePlugin).not.toHaveBeenCalled()
  })

  it('cancels the observation when Halo rejects the disable request', async () => {
    vi.mocked(pluginManagement.disablePlugin).mockRejectedValue(new Error('permission denied'))
    await usePluginOptimizationAction(vi.fn<() => Promise<void>>()).disablePlugin('search', '搜索')
    expect(diagnostics.observationAction).toHaveBeenCalledWith('observation-1', 'cancel')
    expect(diagnostics.observationAction).not.toHaveBeenCalledWith('observation-1', 'confirm')
  })

  it('reports a disabled plugin separately from a failed observation confirmation', async () => {
    vi.mocked(pluginManagement.disablePlugin).mockResolvedValue(undefined)
    vi.mocked(diagnostics.observationAction).mockRejectedValue(new Error('timeout'))
    await usePluginOptimizationAction(vi.fn<() => Promise<void>>()).disablePlugin('search', '搜索')
    expect(Toast.warning).toHaveBeenCalled()
    expect(diagnostics.observationAction).not.toHaveBeenCalledWith('observation-1', 'cancel')
  })
})
