<script setup lang="ts">
import type { PluginDiagnosticDetail } from '@/types/diagnostics'
import { formatDateTime, formatPercent, formatBytes, formatLatency } from '@/utils/format'
defineProps<{ detail: PluginDiagnosticDetail }>()
</script>
<template>
  <section class="diagnostic-section">
    <h2>启停与异常事件</h2>
    <p v-if="!detail.events.length">当前范围内没有插件事件。</p>
    <article
      v-for="(event, index) in detail.events"
      :key="`${event.atMs}:${index}`"
      class="diagnostic-record"
    >
      <strong>{{ event.title }}</strong
      ><time>{{ formatDateTime(event.atMs) }}</time>
      <p>{{ event.summary }}</p>
    </article>
    <h2>优化观察记录</h2>
    <p>展示该插件最近 10 条报告，不受当前时间范围筛选；比较停用前后的 Halo 进程指标。</p>
    <p v-if="!detail.observations.length">暂无该插件的优化观察记录。</p>
    <article
      v-for="observation in detail.observations"
      :key="observation.id"
      class="diagnostic-record"
    >
      <strong>{{ formatDateTime(observation.createdAtMs) }} · {{ observation.state }}</strong>
      <p>{{ observation.message }}</p>
      <p>
        前：CPU {{ formatPercent(observation.before.cpuPercent) }} · 堆
        {{ formatBytes(observation.before.heapBytes) }} · P95
        {{ observation.before.p95Ms == null ? '—' : formatLatency(observation.before.p95Ms) }} ·
        请求 {{ observation.before.requests }}
      </p>
      <p v-if="observation.after">
        后：CPU {{ formatPercent(observation.after.cpuPercent) }} · 堆
        {{ formatBytes(observation.after.heapBytes) }} · P95
        {{ observation.after.p95Ms == null ? '—' : formatLatency(observation.after.p95Ms) }} · 请求
        {{ observation.after.requests }}
      </p>
    </article>
  </section>
</template>
