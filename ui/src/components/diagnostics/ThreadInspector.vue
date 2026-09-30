<script setup lang="ts">
import { VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { ThreadSnapshot } from '@/types/diagnostics'
import { formatPercent, formatDateTime } from '@/utils/format'
const { data, error, loading, refresh } = useDiagnosticResource<ThreadSnapshot>(
  'diagnostic-threads',
  false,
)
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>热点线程与死锁</h2>
        <p>手动读取当前线程栈 · CPU 为最近一次采样区间的单核占比</p>
      </div>
      <VButton size="sm" :loading="loading" @click="refresh()">采集线程栈</VButton>
    </header>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <p v-if="data">
      CPU 已检查 {{ data.inspected }} / {{ data.total }} 个线程 ·
      {{ data.sampledAtMs ? formatDateTime(data.sampledAtMs) : '等待采样' }} ·
      {{ data.cpuAvailable ? '支持 CPU 计数' : '当前 JVM 未开启线程 CPU 计数' }}
    </p>
    <details
      v-for="thread in data?.threads"
      :key="thread.id"
      class="diagnostic-record"
      :open="thread.deadlocked"
    >
      <summary>
        {{ thread.deadlocked ? '死锁 · ' : '' }}{{ thread.name }} · {{ thread.state }} ·
        {{ formatPercent(thread.cpuCorePercent) }}
      </summary>
      <p v-if="thread.lock">等待锁 {{ thread.lock }} · 持有者 {{ thread.lockOwner || '未知' }}</p>
      <pre>{{ thread.stack.join('\n') || '线程已结束或没有可用栈' }}</pre>
    </details>
  </section>
</template>
