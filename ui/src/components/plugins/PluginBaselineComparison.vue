<script setup lang="ts">
import { ref, watch } from 'vue'
import { VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { PluginBaselineComparison, PluginBaselineSummary } from '@/types/diagnostics'
import type { TimeRange } from '@/types/insight'
import { formatDateTime, formatPercent, formatBytes, formatLatency } from '@/utils/format'
const props = defineProps<{ pluginId: string; range: TimeRange }>()
const reference = ref('previous')
const { data, loading, error, refresh } = useDiagnosticResource<PluginBaselineComparison>(
  'plugin-baseline',
  false,
  () => ({ pluginId: props.pluginId, range: props.range, reference: reference.value }),
)
watch([reference, () => props.range], () => void refresh())
const rows: { label: string; value: (summary: PluginBaselineSummary) => string }[] = [
  {
    label: '资源分钟覆盖',
    value: (s) =>
      `${s.observedMinutes} / ${s.expectedMinutes} · ${formatPercent(s.coveragePercent)}`,
  },
  { label: '采样数', value: (s) => String(s.samples) },
  {
    label: 'CPU / 分配量计数分钟',
    value: (s) => `${s.cpuObservedMinutes} / ${s.allocationObservedMinutes}`,
  },
  {
    label: '单核 CPU 估算',
    value: (s) => (s.cpuCorePercent == null ? '不可用' : `${s.cpuCorePercent.toFixed(3)}%`),
  },
  {
    label: '内存分配速率',
    value: (s) =>
      s.allocatedBytesPerMinute == null
        ? '不可用'
        : `${formatBytes(s.allocatedBytesPerMinute)}/min`,
  },
  { label: '请求数', value: (s) => String(s.requests) },
  {
    label: '错误率',
    value: (s) => (s.errorRatePercent == null ? '不可用' : formatPercent(s.errorRatePercent)),
  },
  {
    label: '平均请求耗时',
    value: (s) => (s.averageRequestMs == null ? '不可用' : formatLatency(s.averageRequestMs)),
  },
  { label: '最大驻留线程', value: (s) => String(s.maximumThreads) },
]
</script>
<template>
  <section class="diagnostic-section">
    <h2>性能基线对比</h2>
    <p>使用所选长度的完整时间窗口；升级对比排除版本变更所在分钟</p>
    <div class="diagnostic-controls">
      <label
        >参照<select v-model="reference" aria-label="性能基线参照">
          <option value="previous">前一时段</option>
          <option value="yesterday">昨天同一时段</option>
          <option value="last-week">上周同一时段</option>
          <option value="upgrade">最近升级前后</option>
        </select></label
      ><VButton :loading="loading" @click="refresh()">重新比较</VButton>
    </div>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <template v-if="data"
      ><ul>
        <li v-for="notice in data.notices" :key="notice">{{ notice }}</li>
      </ul>
      <div v-if="data.available && data.before && data.after" class="insight-table-wrap">
        <table class="insight-table">
          <thead>
            <tr>
              <th>指标</th>
              <th>参照窗口</th>
              <th>当前 / 升级后窗口</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>时间范围</td>
              <td>
                {{ formatDateTime(data.before.fromMs) }} — {{ formatDateTime(data.before.toMs) }}
              </td>
              <td>
                {{ formatDateTime(data.after.fromMs) }} — {{ formatDateTime(data.after.toMs) }}
              </td>
            </tr>
            <tr v-for="row in rows" :key="row.label">
              <td>{{ row.label }}</td>
              <td>{{ row.value(data.before) }}</td>
              <td>{{ row.value(data.after) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
