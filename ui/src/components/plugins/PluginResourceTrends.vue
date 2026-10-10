<script setup lang="ts">
import { computed } from 'vue'
import OverviewTrendCard from '@/components/overview/OverviewTrendCard.vue'
import type { PluginDiagnosticDetail } from '@/types/diagnostics'
defineOptions({ name: 'PluginResourceTrends' })
const props = defineProps<{ detail: PluginDiagnosticDetail }>()
const cpu = computed(() =>
  props.detail.timeline
    .filter((point) => point.cpuObservedMinutes > 0)
    .map((point) => ({
      timestampMs: point.atMs,
      cpu: (point.cpuTimeMs / (point.cpuObservedMinutes * 60_000)) * 100,
    })),
)
const allocation = computed(() =>
  props.detail.timeline
    .filter((point) => point.allocationObservedMinutes > 0)
    .map((point) => ({
      timestampMs: point.atMs,
      allocation: point.allocatedBytes / point.allocationObservedMinutes / 1024 / 1024,
    })),
)
</script>
<template>
  <div class="plugin-trends">
    <OverviewTrendCard
      title="单核 CPU 估算"
      description="按有 CPU 计数的分钟平均；共享线程和采样断档可能缺失"
      :points="cpu"
      :series="[{ key: 'cpu', label: '单核 CPU %', color: '#2563eb' }]"
      mode="percent"
      percent-scale="adaptive"
      chart-label="插件单核 CPU 趋势"
      empty-title="暂无 CPU 测量样本"
      :loading="false"
      :from-ms="detail.fromMs"
      :to-ms="detail.toMs"
    />
    <OverviewTrendCard
      title="内存分配速率"
      description="单位 MiB/min，不等于当前持有的内存"
      :points="allocation"
      :series="[{ key: 'allocation', label: 'MiB/min', color: '#7c3aed' }]"
      chart-label="插件内存分配趋势"
      empty-title="暂无分配量测量样本"
      :loading="false"
      :from-ms="detail.fromMs"
      :to-ms="detail.toMs"
    />
  </div>
</template>
<style scoped>
.plugin-trends {
  display: grid;
  gap: 16px;
}
</style>
