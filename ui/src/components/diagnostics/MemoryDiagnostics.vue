<script setup lang="ts">
import { computed } from 'vue'
import { VAlert, VButton, VLoading } from '@halo-dev/components'
import MetricTrendChart from '@/components/dashboard/MetricTrendChart.vue'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { DiagnosticSummary } from '@/types/diagnostics'
import { formatBytes, formatPercent, formatDurationMs } from '@/utils/format'
const { data, error, loading, refresh } = useDiagnosticResource<DiagnosticSummary>('diagnostics')
const points = computed(
  () =>
    data.value?.memory.points.map((point) => ({
      timestampMs: point.atMs,
      heap: point.usedBytes / 1048576,
    })) ?? [],
)
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>近期 GC 与内存趋势</h2>
        <p>最近 1 / 5 / 15 分钟 · 每 15 秒刷新</p>
      </div>
      <VButton size="sm" :loading="loading" @click="refresh()">刷新</VButton>
    </header>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <VLoading v-if="loading && !data" />
    <template v-if="data">
      <VAlert
        v-if="data.historyError"
        type="warning"
        :title="data.historyError"
        :closable="false"
      />
      <div class="diagnostic-metrics">
        <article v-for="item in data.gc" :key="item.minutes">
          <span>最近 {{ item.minutes }} 分钟 GC 时间占比</span>
          <strong>{{ item.sufficient ? formatPercent(item.overheadPercent) : '采样不足' }}</strong>
          <small
            >已观测 {{ formatDurationMs(item.observedMs) }} · {{ item.count }} 次 ·
            {{ item.timeMs }} ms</small
          >
        </article>
      </div>
      <h3>GC 后堆占用 · 分钟最低值（MiB）</h3>
      <VAlert
        v-if="data.memory.sustainedGrowth"
        type="warning"
        :title="'持续增长线索：' + formatBytes(data.memory.growthBytes)"
        :closable="false"
      />
      <MetricTrendChart
        v-if="points.length > 1"
        :points="points"
        :series="[{ key: 'heap', label: 'GC 后堆 MiB', color: '#64748b' }]"
        chart-label="GC 后堆内存趋势"
        :from-ms="data.atMs - 3600000"
        :to-ms="data.atMs"
      />
      <p v-else class="diagnostic-empty">
        {{
          data.memory.available
            ? '等待自然 GC 产生足够样本，不会主动触发 GC。'
            : '当前 JVM 不支持 GC 完成通知。'
        }}
      </p>
      <p>{{ data.memory.explanation }}</p>
      <p v-if="!data.memory.sufficient">增长判断至少需要 10 个分钟样本并覆盖 15 分钟。</p>
    </template>
  </section>
</template>
