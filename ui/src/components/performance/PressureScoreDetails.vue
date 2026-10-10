<script setup lang="ts">
import type { PluginConsumerItem } from '@/types/insight'
import { formatPercent } from '@/utils/format'
defineProps<{ consumer: PluginConsumerItem }>()
</script>

<template>
  <details v-if="consumer.breakdown" class="pressure-details">
    <summary>评分依据</summary>
    <p>
      总分 {{ consumer.pressureScore.toFixed(2) }} · 单核 CPU 估算
      {{ formatPercent(consumer.breakdown.cpuCorePercent) }}
    </p>
    <table class="insight-table">
      <thead>
        <tr>
          <th>维度</th>
          <th>维度分</th>
          <th>贡献分</th>
          <th>强度系数</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="part in consumer.breakdown.components" :key="part.id">
          <td>{{ part.label }}</td>
          <td>{{ part.dimensionScore.toFixed(2) }}</td>
          <td>{{ part.contribution.toFixed(2) }}</td>
          <td>{{ part.intensity.toFixed(3) }}</td>
        </tr>
      </tbody>
    </table>
    <p>
      CPU 有活动 {{ consumer.breakdown.cpuActiveMinutes }} /
      {{ consumer.breakdown.resourceMinutes }} 分钟； 线程驻留
      {{ consumer.breakdown.threadPresentMinutes }} /
      {{ consumer.breakdown.resourceMinutes }} 分钟，最多 {{ consumer.attributedThreadsMax }} 个。
    </p>
    <p>{{ consumer.breakdown.formula }}</p>
  </details>
</template>

<style scoped>
.pressure-details {
  font-size: 11px;
  color: var(--insight-muted);
}
.pressure-details summary {
  cursor: pointer;
  color: var(--insight-primary);
}
.pressure-details[open] {
  min-width: 300px;
  max-width: 520px;
  line-height: 1.6;
}
</style>
