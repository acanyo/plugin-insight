<script setup lang="ts">
import { VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { TaskSnapshot } from '@/types/diagnostics'
import { formatDurationMs, formatDateTime } from '@/utils/format'
const { data, error, loading, refresh } = useDiagnosticResource<TaskSnapshot>('business-tasks')
const history = useDiagnosticResource<TaskSnapshot[]>('business-task-history', false)
const historyData = history.data
async function refreshAll() {
  await Promise.all([refresh(), history.refresh()])
}
const labels: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '运行中',
  SUCCEEDED: '成功',
  FAILED: '失败',
  UNKNOWN: '未知',
}
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>Halo 业务任务</h2>
        <p>{{ data?.coverage }}</p>
      </div>
      <VButton size="sm" :loading="loading" @click="refreshAll">刷新</VButton>
    </header>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <VAlert
      v-if="data && !data.available"
      type="warning"
      title="业务任务状态暂不可用"
      :closable="false"
    />
    <h3>数据备份</h3>
    <div v-if="data?.tasks.length" class="insight-table-wrap">
      <table class="insight-table">
        <thead>
          <tr>
            <th>任务</th>
            <th>状态</th>
            <th>开始时间</th>
            <th>耗时</th>
            <th>失败原因</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in data.tasks" :key="task.id">
            <td>{{ task.id }}</td>
            <td>{{ labels[task.state] }}</td>
            <td>{{ task.startedAtMs ? formatDateTime(task.startedAtMs) : '—' }}</td>
            <td>{{ formatDurationMs(task.durationMs) }}</td>
            <td>{{ task.failureReason || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="diagnostic-empty">当前没有可观测的备份任务。</p>
    <h3>宿主线程池</h3>
    <div v-if="data?.pools.length" class="insight-table-wrap">
      <table class="insight-table">
        <thead>
          <tr>
            <th>线程池</th>
            <th>活动 / 总线程</th>
            <th>就绪排队</th>
            <th>计划或排队</th>
            <th>累计完成</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="pool in data.pools" :key="pool.name">
            <td>{{ pool.name }}</td>
            <td>{{ pool.active }} / {{ pool.threads }}</td>
            <td>{{ pool.readyQueued }}</td>
            <td>{{ pool.scheduledOrQueued }}</td>
            <td>{{ pool.completed }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="diagnostic-empty">宿主未公开可读取的线程池，不能据此判断没有后台任务。</p>
    <details>
      <summary>最近 60 次任务快照</summary>
      <VAlert
        v-if="history.error.value"
        type="error"
        :title="history.error.value"
        :closable="false"
      />
      <p v-for="item in historyData" :key="item.atMs">
        {{ formatDateTime(item.atMs) }} ·
        {{ item.tasks.filter((t) => t.state === 'FAILED').length }} 个失败任务 ·
        {{ item.pools.reduce((sum, p) => sum + p.readyQueued, 0) }} 个就绪排队
      </p>
    </details>
  </section>
</template>
