import { Toast } from '@halo-dev/components'
import { ref } from 'vue'

import { disablePlugin as disablePluginRequest } from '@/api/pluginManagement'
import { prepareObservation, observationAction } from '@/api/diagnostics'
import { isAxiosError } from 'axios'
import { errorMessage } from '@/utils/format'

export function usePluginOptimizationAction(onChanged: () => Promise<unknown>) {
  const disablingPluginId = ref<string>()

  async function disablePlugin(pluginId: string, displayName: string) {
    if (pluginId === 'insight') {
      Toast.error('Insight 不能停用自身来执行诊断。')
      return
    }
    disablingPluginId.value = pluginId
    let observationId: string | undefined
    let disabled = false
    try {
      observationId = (await prepareObservation(pluginId)).id
      await disablePluginRequest(pluginId)
      disabled = true
      await observationAction(observationId, 'confirm')
      Toast.success(`已停用 ${displayName}，将自动对比前后 5 分钟指标，可在诊断中查看`)
      await onChanged()
    } catch (error) {
      if (observationId && !disabled) {
        try {
          await observationAction(observationId, 'cancel')
        } catch {
          /* The prepared record also expires automatically. */
        }
      }
      if (disabled)
        Toast.warning('插件已停用，但观察启动未确认。请到插件管理核实状态；可重新启用后再试。')
      else if (!isAxiosError(error)) Toast.error(errorMessage(error))
    } finally {
      disablingPluginId.value = undefined
    }
  }

  return {
    disablingPluginId,
    disablePlugin,
  }
}
