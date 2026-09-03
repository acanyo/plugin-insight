<script setup lang="ts">
import { VAlert } from '@halo-dev/components'
import { computed } from 'vue'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { REQUEST_HEALTH_THRESHOLDS } from '@/constants/monitoring'
import type {
  HealthStatus,
  PluginRankingItem,
  PluginSort,
  RequestRouteItem,
  SelectOption,
} from '@/types/insight'
import { formatBytes, formatCompactNumber, formatLatency, formatPercent } from '@/utils/format'

const props = defineProps<{
  plugins?: PluginRankingItem[]
  routes?: RequestRouteItem[]
  pluginSort: PluginSort
  pluginsLoading: boolean
  routesLoading: boolean
  pluginsError?: string
  routesError?: string
}>()

const emit = defineEmits<{
  retry: []
  'update:pluginSort': [value: PluginSort]
}>()

const sortOptions: SelectOption<string>[] = [
  { label: 'CPU', value: 'cpu' },
  { label: '内存分配', value: 'memory' },
  { label: '线程', value: 'threads' },
  { label: '请求耗时', value: 'load' },
  { label: '请求', value: 'requests' },
  { label: '延迟', value: 'latency' },
  { label: '错误', value: 'errors' },
]

const sortModel = computed({
  get: () => props.pluginSort,
  set: (value: string) => emit('update:pluginSort', value as PluginSort),
})

function pluginTone(plugin: PluginRankingItem): HealthStatus {
  if (!plugin.enabled) return 'unknown'
  const phase = plugin.phase.toUpperCase()
  if (phase.includes('FAIL') || phase.includes('ERROR')) return 'critical'
  if (phase === 'STARTED' || phase === 'READY') return 'healthy'
  return 'warning'
}

function pluginStatus(plugin: PluginRankingItem): string {
  if (!plugin.enabled) return '已停用'
  return plugin.phase || '未知阶段'
}

const resourceStatus = computed(() => props.plugins?.[0])

function resourceTone(plugin: PluginRankingItem): HealthStatus {
  const availability = plugin.resourceAvailability.toUpperCase()
  if (availability === 'AVAILABLE')
    return plugin.resourceConfidence === 'LOW' ? 'warning' : 'healthy'
  if (availability === 'ERROR') return 'critical'
  if (availability === 'PARTIAL' || availability === 'WARMING_UP') return 'warning'
  return 'unknown'
}

function resourceStatusLabel(plugin: PluginRankingItem): string {
  const availability = plugin.resourceAvailability.toUpperCase()
  const availabilityLabel: Record<string, string> = {
    AVAILABLE: '资源采样可用',
    PARTIAL: '资源采样部分可用',
    WARMING_UP: '资源采样预热中',
    DISABLED: '资源采样已关闭',
    UNSUPPORTED: '当前 JVM 不支持资源采样',
    ERROR: '资源采样异常',
  }
  const confidenceLabel: Record<string, string> = {
    MEDIUM: '中等置信度',
    LOW: '低置信度',
    NONE: '暂无置信度',
  }
  return `${availabilityLabel[availability] || availability} · ${confidenceLabel[plugin.resourceConfidence] || plugin.resourceConfidence}`
}

function formatCpuTime(value: number): string {
  if (!Number.isFinite(value)) return '—'
  if (value < 1_000) return `${Math.round(value)} ms`
  if (value < 60_000) return `${(value / 1_000).toFixed(1)} s`
  return `${(value / 60_000).toFixed(1)} min`
}

function routeTone(route: RequestRouteItem): HealthStatus {
  if (
    route.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.criticalErrorRatePercent ||
    route.p95Ms >= REQUEST_HEALTH_THRESHOLDS.criticalP95Ms
  )
    return 'critical'
  if (
    route.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.warningErrorRatePercent ||
    route.p95Ms >= REQUEST_HEALTH_THRESHOLDS.warningP95Ms
  )
    return 'warning'
  return 'healthy'
}
</script>

<template>
  <div class="performance-panel">
    <section class="insight-section">
      <SectionHeading
        title="插件资源与请求影响排行"
        description="CPU 与内存分配来自有界线程采样；共享线程不强行归属，内存不是当前驻留堆"
      >
        <template #actions>
          <SegmentedControl
            v-model="sortModel"
            :options="sortOptions"
            control-label="插件排行排序"
            compact
          />
        </template>
      </SectionHeading>
      <div v-if="resourceStatus" class="resource-quality">
        <StatusBadge
          :tone="resourceTone(resourceStatus)"
          :label="resourceStatusLabel(resourceStatus)"
        />
        <span>
          CPU 归因覆盖 {{ formatPercent(resourceStatus.cpuAttributionCoveragePercent) }} ·
          内存分配覆盖 {{ formatPercent(resourceStatus.allocationAttributionCoveragePercent) }}
        </span>
        <span class="resource-quality__method insight-mono">
          {{ resourceStatus.resourceAttributionMethod }}
        </span>
      </div>
      <SectionState
        :loading="pluginsLoading && !plugins"
        :error="!plugins ? pluginsError : undefined"
        :empty="!pluginsLoading && !pluginsError && !plugins?.length"
        empty-title="所选时间范围内没有插件活动"
        empty-description="插件线程或请求被采集后，会在这里显示资源归因与请求指标。"
        @retry="emit('retry')"
      >
        <VAlert
          v-if="pluginsError && plugins"
          class="insight-refresh-error"
          type="error"
          :title="`插件排行刷新失败：${pluginsError}`"
          :closable="false"
        />
        <div v-if="plugins" class="insight-table-wrap">
          <table class="insight-table plugin-table">
            <thead>
              <tr>
                <th class="rank-column">排名</th>
                <th>插件</th>
                <th>运行状态</th>
                <th>CPU 时间 / 占比</th>
                <th>区间内存分配 / 占比</th>
                <th class="insight-numeric">归因线程</th>
                <th>请求耗时占比</th>
                <th class="insight-numeric">请求延迟</th>
                <th class="insight-numeric">请求 / 错误率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(plugin, index) in plugins" :key="plugin.pluginId">
                <td class="rank-column insight-mono">{{ String(index + 1).padStart(2, '0') }}</td>
                <td>
                  <div class="plugin-name">
                    <strong>{{ plugin.displayName || plugin.pluginId }}</strong>
                    <span class="insight-mono">{{ plugin.pluginId }}</span>
                  </div>
                </td>
                <td>
                  <StatusBadge :tone="pluginTone(plugin)" :label="pluginStatus(plugin)" />
                </td>
                <td>
                  <div class="resource-value">
                    <strong class="insight-mono">
                      {{ plugin.cpuTimeAvailable ? formatCpuTime(plugin.cpuTimeMs) : '—' }}
                    </strong>
                    <span class="insight-mono">
                      {{
                        plugin.cpuTimeAvailable ? formatPercent(plugin.cpuSharePercent) : '不可用'
                      }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="resource-value">
                    <strong class="insight-mono">
                      {{
                        plugin.allocatedMemoryAvailable
                          ? formatBytes(plugin.allocatedMemoryBytes)
                          : '—'
                      }}
                    </strong>
                    <span class="insight-mono">
                      {{
                        plugin.allocatedMemoryAvailable
                          ? formatPercent(plugin.allocationSharePercent)
                          : '不可用'
                      }}
                    </span>
                  </div>
                </td>
                <td class="insight-numeric insight-mono">
                  {{ plugin.attributedThreadsMax || '—' }}
                </td>
                <td>
                  <div class="impact-cell">
                    <div class="impact-cell__bar">
                      <i :style="{ width: formatPercent(plugin.loadSharePercent) }"></i>
                    </div>
                    <span class="insight-mono">{{ formatPercent(plugin.loadSharePercent) }}</span>
                  </div>
                </td>
                <td class="insight-numeric">
                  <div class="stacked-metric insight-mono">
                    <strong>{{ formatLatency(plugin.averageResponseMs) }}</strong>
                    <span :class="{ danger: plugin.maximumResponseMs >= 2000 }">
                      最大 {{ formatLatency(plugin.maximumResponseMs) }}
                    </span>
                  </div>
                </td>
                <td class="insight-numeric">
                  <div class="stacked-metric insight-mono">
                    <strong>{{ formatCompactNumber(plugin.requestCount) }}</strong>
                    <span :class="{ danger: plugin.errorRatePercent >= 5 }">
                      错误 {{ formatPercent(plugin.errorRatePercent) }}
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </SectionState>
    </section>

    <section class="insight-section">
      <SectionHeading title="慢接口排行" description="按 P95 响应时间排序的归一化请求路径" />
      <SectionState
        :loading="routesLoading && !routes"
        :error="!routes ? routesError : undefined"
        :empty="!routesLoading && !routesError && !routes?.length"
        empty-title="没有检测到慢接口"
        @retry="emit('retry')"
      >
        <VAlert
          v-if="routesError && routes"
          class="insight-refresh-error"
          type="error"
          :title="`慢接口排行刷新失败：${routesError}`"
          :closable="false"
        />
        <div v-if="routes" class="insight-table-wrap">
          <table class="insight-table route-table">
            <thead>
              <tr>
                <th>接口</th>
                <th>插件 / 作用域</th>
                <th>状态</th>
                <th class="insight-numeric">请求</th>
                <th class="insight-numeric">平均</th>
                <th class="insight-numeric">P95</th>
                <th class="insight-numeric">P99</th>
                <th class="insight-numeric">最大</th>
                <th class="insight-numeric">错误率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="route in routes" :key="`${route.scope}-${route.method}-${route.route}`">
                <td>
                  <div class="route-name">
                    <span class="method-badge">{{ route.method }}</span>
                    <code>{{ route.route }}</code>
                  </div>
                </td>
                <td class="scope-cell insight-mono">{{ route.scope || 'halo' }}</td>
                <td><StatusBadge :tone="routeTone(route)" /></td>
                <td class="insight-numeric insight-mono">
                  {{ formatCompactNumber(route.requestCount) }}
                </td>
                <td class="insight-numeric insight-mono">
                  {{ formatLatency(route.averageLatencyMs) }}
                </td>
                <td class="insight-numeric insight-mono">{{ formatLatency(route.p95Ms) }}</td>
                <td class="insight-numeric insight-mono" :class="{ danger: route.p99Ms >= 2000 }">
                  {{ formatLatency(route.p99Ms) }}
                </td>
                <td class="insight-numeric insight-mono">
                  {{ formatLatency(route.maximumLatencyMs) }}
                </td>
                <td
                  class="insight-numeric insight-mono"
                  :class="{ danger: route.errorRatePercent >= 5 }"
                >
                  {{ formatPercent(route.errorRatePercent) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </SectionState>
    </section>
  </div>
</template>

<style scoped>
.performance-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.plugin-table {
  min-width: 1280px;
}

.rank-column {
  width: 58px;
  color: var(--insight-subtle);
  text-align: center !important;
}

.danger {
  color: var(--insight-red);
}

.plugin-name {
  display: flex;
  min-width: 160px;
  max-width: 260px;
  flex-direction: column;
}

.plugin-name strong,
.plugin-name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-name strong {
  font-size: 13px;
  font-weight: 630;
}

.plugin-name span {
  color: var(--insight-muted);
  font-size: 10px;
}

.impact-cell {
  display: grid;
  min-width: 170px;
  align-items: center;
  grid-template-columns: minmax(82px, 1fr) 49px;
  gap: 9px;
}

.impact-cell__bar {
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: #edf0f2;
}

.impact-cell__bar i {
  display: block;
  max-width: 100%;
  height: 100%;
  background: var(--insight-cyan);
}

.resource-quality {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-top: 1px solid var(--insight-border);
  border-bottom: 1px solid var(--insight-border);
  color: var(--insight-muted);
  background: var(--insight-surface-muted);
  font-size: 11px;
}

.resource-quality__method {
  margin-left: auto;
  color: var(--insight-subtle);
  font-size: 9px;
}

.resource-value,
.stacked-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  white-space: nowrap;
}

.resource-value strong,
.stacked-metric strong {
  font-size: 12px;
  font-weight: 650;
}

.resource-value span,
.stacked-metric span {
  color: var(--insight-muted);
  font-size: 9px;
}

.stacked-metric {
  align-items: flex-end;
}

.route-table {
  min-width: 1040px;
}

.route-name {
  display: flex;
  min-width: 250px;
  align-items: center;
  gap: 9px;
}

.route-name code {
  overflow: hidden;
  color: var(--insight-ink);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.method-badge {
  display: inline-flex;
  min-width: 46px;
  min-height: 21px;
  align-items: center;
  justify-content: center;
  padding: 2px 5px;
  border: 1px solid #b7dde3;
  border-radius: 4px;
  color: var(--insight-cyan);
  background: var(--insight-cyan-soft);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
}

.scope-cell {
  max-width: 170px;
  overflow: hidden;
  color: var(--insight-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .resource-quality {
    align-items: flex-start;
    flex-direction: column;
  }

  .resource-quality__method {
    margin-left: 0;
  }
}
</style>
