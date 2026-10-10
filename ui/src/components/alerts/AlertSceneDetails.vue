<script setup lang="ts">
import { computed } from 'vue'
import type { AlertIncident } from '@/types/insight'
import { alertScene } from '@/utils/alertScene'
import { formatDateTime, formatPercent, formatLatency, formatBytes } from '@/utils/format'
const props = defineProps<{ alert: AlertIncident }>()
const scene = computed(() => alertScene(props.alert))
</script>
<template>
  <details v-if="scene" class="alert-scene">
    <summary>查看首次触发现场 · {{ formatDateTime(scene.capturedAtMs) }}</summary>
    <p>
      采样时间 {{ scene.sampledAtMs ? formatDateTime(scene.sampledAtMs) : '不可用' }} · 进程 CPU
      {{ scene.processCpuPercent == null ? '—' : formatPercent(scene.processCpuPercent) }} · 堆
      {{ scene.heapUsedPercent == null ? '—' : formatPercent(scene.heapUsedPercent) }} · GC
      {{ scene.gcPercent == null ? '—' : formatPercent(scene.gcPercent) }}
    </p>
    <p>
      请求窗口 {{ formatDateTime(scene.requestFromMs) }} — {{ formatDateTime(scene.requestToMs) }} ·
      请求 {{ scene.requestCount }} · P95
      {{ scene.p95Ms == null ? '—' : formatLatency(scene.p95Ms) }}
    </p>
    <p v-if="!scene.plugins.length">当时没有可用的插件排行证据。</p>
    <ul>
      <li v-for="plugin in scene.plugins" :key="plugin.pluginId">
        {{ plugin.displayName || plugin.pluginId }} · 压力 {{ plugin.pressureScore.toFixed(1) }} ·
        CPU 份额 {{ formatPercent(plugin.cpuSharePercent) }} · 分配
        {{ formatBytes(plugin.allocatedBytesPerMinute) }}/min
      </li>
    </ul>
    <p v-if="!scene.routes.length">当时没有可用的接口排行证据。</p>
    <ul>
      <li v-for="route in scene.routes" :key="`${route.method}:${route.route}`">
        {{ route.method }} {{ route.route }} · P95 {{ formatLatency(route.p95Ms) }} · 请求 / 错误
        {{ route.requests }} / {{ route.errors }}
      </li>
    </ul>
    <p>{{ scene.coverage }}</p>
  </details>
</template>
<style scoped>
.alert-scene {
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.6;
}
.alert-scene summary {
  cursor: pointer;
  color: var(--insight-primary);
}
</style>
