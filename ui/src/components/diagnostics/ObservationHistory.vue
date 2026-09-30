<script setup lang="ts">
import { ref } from 'vue'
import { utils } from '@halo-dev/ui-shared'
import { Dialog, VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import { enablePlugin } from '@/api/pluginManagement'
import { observationAction } from '@/api/diagnostics'
import type { Observation } from '@/types/diagnostics'
import { formatBytes, formatDateTime, formatPercent, errorMessage } from '@/utils/format'
const { data, error, loading, refresh } = useDiagnosticResource<Observation[]>('observations')
const busy = ref<string>()
const actionError = ref<string>()
const labels: Record<string, string> = {
  prepared: '等待停用',
  observing: '观察中',
  completed: '已完成',
  interrupted: '已中断',
  restored: '已重新启用',
  cancelled: '已取消',
}
const canRestore = () =>
  utils.permission.has(['system:plugins:manage', 'plugin:insight:manage'], false)
function restore(item: Observation) {
  Dialog.warning({
    title: '重新启用插件',
    description: `确认重新启用 ${item.displayName || item.pluginId}？插件恢复运行后会重新消耗资源。`,
    confirmText: '重新启用',
    onConfirm: async () => {
      busy.value = item.id
      actionError.value = undefined
      try {
        await enablePlugin(item.pluginId)
        await observationAction(item.id, 'restored')
        await refresh()
      } catch (error) {
        actionError.value = errorMessage(error)
      } finally {
        busy.value = undefined
      }
    },
  })
}
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>优化观察记录</h2>
        <p>通过“性能诊断”停用插件后，自动比较前后各 5 个完整分钟</p>
      </div>
      <VButton size="sm" :loading="loading" @click="refresh()">刷新</VButton>
    </header>
    <VAlert
      v-if="error || actionError"
      type="error"
      :title="error || actionError || ''"
      :closable="false"
    />
    <p v-if="!loading && !data?.length" class="diagnostic-empty">
      暂无优化观察。先从持续高消耗插件开始诊断。
    </p>
    <article v-for="item in data" :key="item.id" class="diagnostic-record">
      <header>
        <div>
          <h3>{{ item.displayName || item.pluginId }} · {{ labels[item.state] || item.state }}</h3>
          <p>{{ formatDateTime(item.createdAtMs) }}</p>
        </div>
        <VButton
          v-if="
            canRestore() &&
            item.state !== 'restored' &&
            item.state !== 'prepared' &&
            item.state !== 'cancelled'
          "
          size="sm"
          :loading="busy === item.id"
          @click="restore(item)"
          >重新启用</VButton
        >
      </header>
      <p>{{ item.message }}</p>
      <p v-if="item.state === 'observing'">
        预计 {{ formatDateTime(item.toMs + 30000) }} 生成对比。
      </p>
      <div class="insight-table-wrap">
        <table class="insight-table">
          <thead>
            <tr>
              <th>指标</th>
              <th>停用前</th>
              <th>停用后</th>
              <th>变化</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>CPU 均值</td>
              <td>{{ formatPercent(item.before.cpuPercent) }}</td>
              <td>{{ item.after ? formatPercent(item.after.cpuPercent) : '—' }}</td>
              <td>
                {{
                  item.after
                    ? (item.after.cpuPercent - item.before.cpuPercent).toFixed(1) + ' 个百分点'
                    : '—'
                }}
              </td>
            </tr>
            <tr>
              <td>堆占用均值</td>
              <td>{{ formatBytes(item.before.heapBytes) }}</td>
              <td>{{ item.after ? formatBytes(item.after.heapBytes) : '—' }}</td>
              <td>
                {{
                  item.after
                    ? (item.after.heapBytes >= item.before.heapBytes ? '+' : '−') +
                      formatBytes(Math.abs(item.after.heapBytes - item.before.heapBytes))
                    : '—'
                }}
              </td>
            </tr>
            <tr>
              <td>GC 时间占比</td>
              <td>{{ formatPercent(item.before.gcPercent) }}</td>
              <td>{{ item.after ? formatPercent(item.after.gcPercent) : '—' }}</td>
              <td>
                {{
                  item.after
                    ? (item.after.gcPercent - item.before.gcPercent).toFixed(1) + ' 个百分点'
                    : '—'
                }}
              </td>
            </tr>
            <tr>
              <td>P95（直方图估算）</td>
              <td>{{ item.before.p95Ms == null ? '无请求' : item.before.p95Ms + ' ms' }}</td>
              <td>{{ item.after?.p95Ms == null ? '—' : item.after.p95Ms + ' ms' }}</td>
              <td>
                {{
                  item.after?.p95Ms != null && item.before.p95Ms != null
                    ? item.after.p95Ms - item.before.p95Ms + ' ms'
                    : '—'
                }}
              </td>
            </tr>
            <tr>
              <td>请求量</td>
              <td>{{ item.before.requests }}</td>
              <td>{{ item.after?.requests ?? '—' }}</td>
              <td>{{ item.after ? item.after.requests - item.before.requests : '—' }}</td>
            </tr>
            <tr>
              <td>样本覆盖</td>
              <td>{{ item.before.sufficient ? '充分' : '不足' }} · {{ item.before.samples }} 个</td>
              <td>
                {{
                  item.after
                    ? (item.after.sufficient ? '充分' : '不足') + ' · ' + item.after.samples + ' 个'
                    : '—'
                }}
              </td>
              <td>相同长度时间窗</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
