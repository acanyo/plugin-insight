<script setup lang="ts">
import { VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { Delivery } from '@/types/diagnostics'
import { formatDateTime } from '@/utils/format'
const { data, error, loading, refresh } = useDiagnosticResource<Delivery[]>('deliveries')
const labels: Record<string, string> = {
  pending: '等待重试',
  sent: '已提交渠道',
  skipped: '已跳过',
  failed: '投递失败',
  superseded: '已取消旧提醒',
}
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>通知投递历史</h2>
        <p>最近 100 条 · 失败最多尝试 5 次，等待 30 / 60 / 120 / 240 秒后重试</p>
      </div>
      <VButton size="sm" :loading="loading" @click="refresh()">刷新</VButton>
    </header>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <div v-if="data?.length" class="insight-table-wrap">
      <table class="insight-table">
        <thead>
          <tr>
            <th>创建时间</th>
            <th>渠道</th>
            <th>状态</th>
            <th>尝试次数</th>
            <th>下次尝试</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in data" :key="row.id">
            <td>{{ formatDateTime(row.createdAtMs) }}</td>
            <td>{{ row.channel }}</td>
            <td>{{ labels[row.state] }}</td>
            <td>{{ row.attempts }}</td>
            <td>{{ row.state === 'pending' ? formatDateTime(row.nextAtMs) : '—' }}</td>
            <td>{{ row.message }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="diagnostic-empty">暂无投递记录，可在告警中心测试通知渠道。</p>
    <p>
      渠道接受不代表收件人已阅读。采用至少一次投递，故障恢复时可能重复；Webhook 接收方可按
      incidentId、occurredAt 和 recovered 去重。
    </p>
  </section>
</template>
