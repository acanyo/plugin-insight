import { Toast } from '@halo-dev/components'
import { ref } from 'vue'

import {
  applyOptimization as applyOptimizationRequest,
  fetchOptimization,
  rollbackOptimization as rollbackOptimizationRequest,
} from '@/api/insight'
import type { OptimizationStatus } from '@/types/insight'
import { errorMessage } from '@/utils/format'
import { createLatestRequestChannel } from '@/utils/latestRequest'

import { createResource, loadLatestResource } from './resourceState'

export function useOptimizationResource(onChanged: () => Promise<unknown>) {
  const optimization = createResource<OptimizationStatus>()
  const requests = createLatestRequestChannel()
  const applying = ref(false)
  const rollingBack = ref(false)

  function refreshOptimization(silent = false): Promise<boolean> {
    return loadLatestResource(optimization, requests, (signal) => fetchOptimization(signal), silent)
  }

  async function applyOptimization() {
    applying.value = true
    try {
      optimization.data.value = await applyOptimizationRequest()
      optimization.error.value = undefined
      Toast.success('已降低 Insight 自身监控开销')
      await onChanged()
    } catch (error) {
      Toast.error(errorMessage(error))
    } finally {
      applying.value = false
    }
  }

  async function rollbackOptimization() {
    rollingBack.value = true
    try {
      optimization.data.value = await rollbackOptimizationRequest()
      optimization.error.value = undefined
      Toast.success('已恢复 Insight 标准采集')
      await onChanged()
    } catch (error) {
      Toast.error(errorMessage(error))
    } finally {
      rollingBack.value = false
    }
  }

  return {
    optimization,
    applying,
    rollingBack,
    refreshOptimization,
    applyOptimization,
    rollbackOptimization,
    abortOptimizationRequests: requests.abort,
  }
}
