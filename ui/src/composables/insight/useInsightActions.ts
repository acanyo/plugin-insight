import { Toast } from '@halo-dev/components'
import { ref, type Ref } from 'vue'

import { deleteData, downloadExport, testNotifications } from '@/api/insight'
import type { TimeRange } from '@/types/insight'
import { errorMessage } from '@/utils/format'

export function useInsightActions(
  range: Ref<TimeRange>,
  onDataCleared: () => Promise<unknown>,
) {
  const exporting = ref(false)
  const testingNotifications = ref(false)
  const clearingData = ref(false)

  async function sendTestNotification() {
    testingNotifications.value = true
    try {
      const results = await testNotifications()
      const sent = results.filter((result) => result.sent).length
      if (sent > 0) {
        Toast.success(`测试通知已通过 ${sent} 个渠道发送`)
      } else {
        Toast.error(results.map((result) => result.message).join('；') || '没有可用的通知渠道')
      }
    } catch (error) {
      Toast.error(errorMessage(error))
    } finally {
      testingNotifications.value = false
    }
  }

  async function clearMonitoringData() {
    clearingData.value = true
    try {
      const result = await deleteData('all')
      Toast.success(`已清理 ${result.deletedRows} 行监控历史`)
      await onDataCleared()
    } catch (error) {
      Toast.error(errorMessage(error))
    } finally {
      clearingData.value = false
    }
  }

  async function exportData() {
    exporting.value = true
    try {
      await downloadExport(range.value)
      Toast.success('监控数据已导出')
    } catch (error) {
      Toast.error(errorMessage(error))
    } finally {
      exporting.value = false
    }
  }

  return {
    exporting,
    testingNotifications,
    clearingData,
    sendTestNotification,
    clearMonitoringData,
    exportData,
  }
}
