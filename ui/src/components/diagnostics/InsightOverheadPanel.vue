<script setup lang="ts">
import { VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { OverheadSnapshot } from '@/types/diagnostics'
import { formatBytes, formatDateTime } from '@/utils/format'
const { data, loading, error, refresh } = useDiagnosticResource<OverheadSnapshot>(
  'monitor-overhead',
  false,
)
</script>

<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>Insight 自身开销</h2>
        <p>测量既有任务，按需刷新；窗口内数据不额外写入数据库</p>
      </div>
      <VButton :loading="loading" @click="refresh()">刷新</VButton>
    </header>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <template v-if="data">
      <div class="overhead-summary">
        <span
          >采集与诊断单核 CPU
          <strong>{{
            data.cpuAvailable ? `${data.cpuCorePercent.toFixed(3)}%` : '不可用'
          }}</strong></span
        >
        <span
          >分配速率
          <strong>{{
            data.allocationAvailable ? `${formatBytes(data.allocatedBytesPerMinute)}/min` : '不可用'
          }}</strong></span
        >
        <span
          >SQLite 读取 <strong>{{ data.databaseReads }}</strong></span
        ><span
          >SQLite 写入 <strong>{{ data.databaseWrites }}</strong></span
        >
      </div>
      <p>{{ formatDateTime(data.fromMs) }} — {{ formatDateTime(data.toMs) }}</p>
      <div class="insight-table-wrap">
        <table class="insight-table">
          <thead>
            <tr>
              <th>操作</th>
              <th>次数 / 失败</th>
              <th>平均耗时</th>
              <th>最大耗时</th>
              <th>CPU 时间</th>
              <th>分配量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="operation in data.operations" :key="operation.id">
              <td>{{ operation.label }}</td>
              <td>{{ operation.calls }} / {{ operation.failures }}</td>
              <td>{{ operation.averageMs.toFixed(2) }} ms</td>
              <td>{{ operation.maximumMs.toFixed(2) }} ms</td>
              <td>{{ operation.cpuSamples ? `${operation.cpuTimeMs.toFixed(2)} ms` : '—' }}</td>
              <td>
                {{ operation.allocationSamples ? formatBytes(operation.allocatedBytes) : '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <details v-if="data.points.length">
        <summary>每分钟记录 · {{ data.points.length }} 个点</summary>
        <div class="insight-table-wrap">
          <table class="insight-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>CPU 时间</th>
                <th>分配量</th>
                <th>读 / 写</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="point in data.points" :key="point.atMs">
                <td>{{ formatDateTime(point.atMs) }}</td>
                <td>{{ data.cpuAvailable ? `${point.cpuTimeMs.toFixed(2)} ms` : '—' }}</td>
                <td>{{ data.allocationAvailable ? formatBytes(point.allocatedBytes) : '—' }}</td>
                <td>{{ point.databaseReads }} / {{ point.databaseWrites }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
      <p>{{ data.coverage }}</p>
    </template>
  </section>
</template>
<style scoped>
.overhead-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 12px;
}
.overhead-summary span {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
