<script setup lang="ts">
import type { RequestRouteItem } from '@/types/insight'
import { formatLatency, formatPercent } from '@/utils/format'
defineProps<{ routes: RequestRouteItem[] }>()
</script>
<template>
  <section class="diagnostic-section">
    <h2>慢接口与错误</h2>
    <p>按 P95 从高到低展示该插件的最多 10 个接口</p>
    <p v-if="!routes.length">当前范围内没有可归属的插件请求记录。</p>
    <div v-else class="insight-table-wrap">
      <table class="insight-table">
        <thead>
          <tr>
            <th>接口</th>
            <th>请求 / 错误</th>
            <th>错误率</th>
            <th>平均</th>
            <th>P95</th>
            <th>最大</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="route in routes" :key="`${route.method}:${route.route}`">
            <td>{{ route.method }} {{ route.route }}</td>
            <td>{{ route.requestCount }} / {{ route.errorCount }}</td>
            <td>{{ formatPercent(route.errorRatePercent) }}</td>
            <td>{{ formatLatency(route.averageLatencyMs) }}</td>
            <td>{{ formatLatency(route.p95Ms) }}</td>
            <td>{{ formatLatency(route.maximumLatencyMs) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
