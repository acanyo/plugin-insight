<script setup lang="ts">
import { VAlert } from '@halo-dev/components'
import { useAutoAnimate } from '@formkit/auto-animate/vue'
import { computed, ref, watch } from 'vue'
import RiArrowDownSLine from '~icons/ri/arrow-down-s-line'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import PluginIssueButton from '@/components/common/PluginIssueButton.vue'
import RouteSortControl from '@/components/performance/RouteSortControl.vue'
import SustainedConsumersSection from '@/components/performance/SustainedConsumersSection.vue'
import { REQUEST_HEALTH_THRESHOLDS } from '@/constants/monitoring'
import { ROUTE_RANKING_PRESENTATION, type RouteSortColumn } from '@/constants/routeRanking'
import type {
  HealthStatus,
  PluginConsumerItem,
  PluginRankingItem,
  PluginRuntimeInfo,
  PluginSort,
  RequestRouteItem,
  RequestRouteSort,
  SelectOption,
} from '@/types/insight'
import type { IssueEnvironment } from '@/utils/pluginIssue'
import {
  formatBytes,
  formatCompactNumber,
  formatDateTime,
  formatLatency,
  formatPercent,
} from '@/utils/format'

const props = defineProps<{
  plugins?: PluginRankingItem[]
  consumers?: PluginConsumerItem[]
  routes?: RequestRouteItem[]
  pluginSort: PluginSort
  routeSort?: RequestRouteSort
  pluginsLoading: boolean
  consumersLoading?: boolean
  routesLoading: boolean
  pluginsError?: string
  consumersError?: string
  routesError?: string
  environment?: IssueEnvironment
  runtimePlugins?: PluginRuntimeInfo[]
}>()

const emit = defineEmits<{
  retry: []
  'update:pluginSort': [value: PluginSort]
  'update:routeSort': [value: RequestRouteSort]
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

const fallbackRouteSort = ref<RequestRouteSort>(props.routeSort ?? 'slow')
const expandedPluginIds = ref(new Set<string>())
const expandedRouteKeys = ref(new Set<string>())
const [pluginRows] = useAutoAnimate<HTMLTableSectionElement>({
  duration: 180,
  easing: 'ease-out',
})
const [routeRows] = useAutoAnimate<HTMLTableSectionElement>({
  duration: 180,
  easing: 'ease-out',
})
const routeSortModel = computed<RequestRouteSort>({
  get: () => props.routeSort ?? fallbackRouteSort.value,
  set: (value) => {
    fallbackRouteSort.value = value
    emit('update:routeSort', value)
  },
})

watch(
  () => props.routeSort,
  (value) => {
    if (value) fallbackRouteSort.value = value
  },
)

const routeRankingPresentation = computed(() => ROUTE_RANKING_PRESENTATION[routeSortModel.value])

function routeColumnSort(column: RouteSortColumn): 'descending' | 'none' {
  return routeRankingPresentation.value.sortColumn === column ? 'descending' : 'none'
}

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

function formatAllocationRate(value: number): string {
  return `${formatBytes(value)}/s`
}

function formatCpuPercent(value: number): string {
  if (!Number.isFinite(value)) return '—'
  return `${Math.max(0, value).toFixed(1)}%`
}

function runtimePlugin(pluginId: string): PluginRuntimeInfo | undefined {
  return props.runtimePlugins?.find(
    (plugin) => plugin.name.toLowerCase() === pluginId.toLowerCase(),
  )
}

function shouldOfferIssue(plugin: PluginRankingItem): boolean {
  return Boolean(
    (plugin.liveCpuAvailable && plugin.liveCpuPercent >= 50) ||
    (plugin.liveAllocationAvailable && plugin.liveAllocationBytesPerSecond >= 1_024 * 1_024) ||
    plugin.loadSharePercent >= 25,
  )
}

function isPluginExpanded(pluginId: string): boolean {
  return expandedPluginIds.value.has(pluginId)
}

function togglePlugin(pluginId: string): void {
  const next = new Set(expandedPluginIds.value)
  if (next.has(pluginId)) next.delete(pluginId)
  else next.add(pluginId)
  expandedPluginIds.value = next
}

function routeKey(route: RequestRouteItem): string {
  return `${route.scope}-${route.method}-${route.route}`
}

function isRouteExpanded(route: RequestRouteItem): boolean {
  return expandedRouteKeys.value.has(routeKey(route))
}

function toggleRoute(route: RequestRouteItem): void {
  const key = routeKey(route)
  const next = new Set(expandedRouteKeys.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedRouteKeys.value = next
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
    <SustainedConsumersSection
      :consumers="consumers"
      :loading="consumersLoading ?? false"
      :error="consumersError"
      :environment="environment"
      @retry="emit('retry')"
    />

    <section class="insight-section">
      <SectionHeading
        title="插件 CPU 与内存分配排行"
        description="列出已识别的全部插件；当前 CPU 和内存为最近采样周期估算值，区间值用于回看趋势；内存不代表当前驻留堆"
      >
        <template #actions>
          <label class="sort-select">
            <span>排序</span>
            <select v-model="sortModel" aria-label="插件排行排序">
              <option v-for="option in sortOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
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
        <span v-if="resourceStatus.liveSampledAtMs > 0">
          当前样本 {{ formatDateTime(new Date(resourceStatus.liveSampledAtMs).toISOString()) }} ·
          采样周期 {{ (resourceStatus.liveSampleIntervalMs / 1000).toFixed(1) }} 秒
        </span>
      </div>
      <SectionState
        :loading="pluginsLoading && !plugins"
        :error="!plugins ? pluginsError : undefined"
        :empty="!pluginsLoading && !pluginsError && !plugins?.length"
        empty-title="暂无插件信息"
        empty-description="Halo 插件状态刷新后，会在这里显示资源归因与请求指标。"
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
                <th class="rank-column" aria-label="展开详情"></th>
                <th>插件</th>
                <th>运行状态</th>
                <th>当前 CPU</th>
                <th>内存分配速率</th>
                <th>请求耗时占比</th>
                <th class="insight-numeric">请求 / 错误</th>
                <th>反馈</th>
              </tr>
            </thead>
            <tbody ref="pluginRows">
              <template v-for="plugin in plugins" :key="plugin.pluginId">
                <tr>
                  <td class="rank-column">
                    <button
                      type="button"
                      class="plugin-expand"
                      :class="{ 'plugin-expand--open': isPluginExpanded(plugin.pluginId) }"
                      :aria-expanded="isPluginExpanded(plugin.pluginId)"
                      :aria-label="`${isPluginExpanded(plugin.pluginId) ? '收起' : '展开'} ${plugin.displayName || plugin.pluginId} 详情`"
                      @click="togglePlugin(plugin.pluginId)"
                    >
                      <RiArrowDownSLine aria-hidden="true" />
                    </button>
                  </td>
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
                    <strong class="table-primary-value insight-mono">
                      {{ plugin.liveCpuAvailable ? formatCpuPercent(plugin.liveCpuPercent) : '—' }}
                    </strong>
                  </td>
                  <td>
                    <strong class="table-primary-value insight-mono">
                      {{
                        plugin.liveAllocationAvailable
                          ? formatAllocationRate(plugin.liveAllocationBytesPerSecond)
                          : '—'
                      }}
                    </strong>
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
                      <strong>{{ formatCompactNumber(plugin.requestCount) }}</strong>
                      <span :class="{ danger: plugin.errorRatePercent >= 5 }">
                        错误 {{ formatPercent(plugin.errorRatePercent) }}
                      </span>
                    </div>
                  </td>
                  <td>
                    <PluginIssueButton
                      v-if="shouldOfferIssue(plugin) && runtimePlugin(plugin.pluginId)"
                      :plugin="runtimePlugin(plugin.pluginId)"
                      :environment="environment"
                    />
                  </td>
                </tr>
                <tr v-if="isPluginExpanded(plugin.pluginId)" class="plugin-detail-row">
                  <td colspan="8">
                    <Transition name="plugin-detail" appear>
                      <div class="plugin-detail-grid">
                        <div class="plugin-detail-item">
                          <span>区间 CPU 时间</span>
                          <strong class="insight-mono">{{
                            plugin.cpuTimeAvailable ? formatCpuTime(plugin.cpuTimeMs) : '不可用'
                          }}</strong>
                        </div>
                        <div class="plugin-detail-item">
                          <span>区间分配量</span>
                          <strong class="insight-mono">{{
                            plugin.allocatedMemoryAvailable
                              ? formatBytes(plugin.allocatedMemoryBytes)
                              : '不可用'
                          }}</strong>
                        </div>
                        <div class="plugin-detail-item">
                          <span>归因线程（当前 / 峰值）</span>
                          <strong class="insight-mono"
                            >{{ plugin.liveThreadsAvailable ? plugin.liveThreads : '—' }} /
                            {{ plugin.attributedThreadsMax || '—' }}</strong
                          >
                        </div>
                        <div class="plugin-detail-item">
                          <span>平均 / 最大延迟</span>
                          <strong class="insight-mono"
                            >{{ formatLatency(plugin.averageResponseMs) }} /
                            {{ formatLatency(plugin.maximumResponseMs) }}</strong
                          >
                        </div>
                      </div>
                    </Transition>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </SectionState>
      <p class="resource-note">
        当前 CPU 以单核 100% 计，多线程可以超过 100%；“—”表示无法归因或样本过期。 每 30
        秒刷新，内存显示分配速率，插件共享 JVM 堆，无法像独立进程一样计算各自 RSS。
      </p>
    </section>

    <section class="insight-section">
      <SectionHeading
        :title="routeRankingPresentation.title"
        :description="routeRankingPresentation.description"
      >
        <template #actions>
          <RouteSortControl v-model="routeSortModel" />
        </template>
      </SectionHeading>
      <SectionState
        :loading="routesLoading && !routes"
        :error="!routes ? routesError : undefined"
        :empty="!routesLoading && !routesError && !routes?.length"
        :empty-title="routeRankingPresentation.emptyTitle"
        :empty-description="routeRankingPresentation.emptyDescription"
        @retry="emit('retry')"
      >
        <VAlert
          v-if="routesError && routes"
          class="insight-refresh-error"
          type="error"
          :title="`${routeRankingPresentation.title}刷新失败：${routesError}`"
          :closable="false"
        />
        <div v-if="routes" class="insight-table-wrap">
          <table class="insight-table route-table">
            <thead>
              <tr>
                <th class="rank-column" aria-label="展开详情"></th>
                <th>接口</th>
                <th>插件 / 作用域</th>
                <th>状态</th>
                <th
                  class="insight-numeric"
                  :aria-sort="
                    routeColumnSort('requests') === 'descending' ||
                    routeColumnSort('errors') === 'descending'
                      ? 'descending'
                      : 'none'
                  "
                >
                  请求 / 错误
                  <span
                    v-if="
                      routeColumnSort('requests') === 'descending' ||
                      routeColumnSort('errors') === 'descending'
                    "
                    class="route-sort-marker"
                    aria-hidden="true"
                    >↓</span
                  >
                </th>
                <th class="insight-numeric" :aria-sort="routeColumnSort('p95')">
                  P95
                  <span
                    v-if="routeColumnSort('p95') === 'descending'"
                    class="route-sort-marker"
                    aria-hidden="true"
                    >↓</span
                  >
                </th>
                <th class="insight-numeric" :aria-sort="routeColumnSort('error-rate')">
                  错误率
                  <span
                    v-if="routeColumnSort('error-rate') === 'descending'"
                    class="route-sort-marker"
                    aria-hidden="true"
                    >↓</span
                  >
                </th>
              </tr>
            </thead>
            <tbody ref="routeRows">
              <template v-for="route in routes" :key="routeKey(route)">
                <tr>
                  <td class="rank-column">
                    <button
                      type="button"
                      class="plugin-expand"
                      :class="{ 'plugin-expand--open': isRouteExpanded(route) }"
                      :aria-expanded="isRouteExpanded(route)"
                      :aria-label="`${isRouteExpanded(route) ? '收起' : '展开'} ${route.route} 详情`"
                      @click="toggleRoute(route)"
                    >
                      <RiArrowDownSLine aria-hidden="true" />
                    </button>
                  </td>
                  <td>
                    <div class="route-name">
                      <span class="method-badge">{{ route.method }}</span>
                      <code>{{ route.route }}</code>
                    </div>
                  </td>
                  <td class="scope-cell insight-mono">{{ route.scope || 'halo' }}</td>
                  <td><StatusBadge :tone="routeTone(route)" /></td>
                  <td class="insight-numeric">
                    <div class="stacked-metric insight-mono">
                      <strong>{{ formatCompactNumber(route.requestCount) }}</strong>
                      <span :class="{ danger: route.errorCount > 0 }">
                        错误 {{ formatCompactNumber(route.errorCount) }}
                      </span>
                    </div>
                  </td>
                  <td class="insight-numeric insight-mono">{{ formatLatency(route.p95Ms) }}</td>
                  <td
                    class="insight-numeric insight-mono"
                    :class="{ danger: route.errorRatePercent >= 5 }"
                  >
                    {{ formatPercent(route.errorRatePercent) }}
                  </td>
                </tr>
                <tr v-if="isRouteExpanded(route)" class="plugin-detail-row">
                  <td colspan="7">
                    <Transition name="plugin-detail" appear>
                      <div class="plugin-detail-grid route-detail-grid">
                        <div class="plugin-detail-item">
                          <span>请求量 / 错误数</span>
                          <strong class="insight-mono">
                            {{ formatCompactNumber(route.requestCount) }} /
                            {{ formatCompactNumber(route.errorCount) }}
                          </strong>
                        </div>
                        <div class="plugin-detail-item">
                          <span>平均延迟</span>
                          <strong class="insight-mono">{{
                            formatLatency(route.averageLatencyMs)
                          }}</strong>
                        </div>
                        <div class="plugin-detail-item">
                          <span>P99 延迟</span>
                          <strong class="insight-mono">{{ formatLatency(route.p99Ms) }}</strong>
                        </div>
                        <div class="plugin-detail-item">
                          <span>最大延迟</span>
                          <strong class="insight-mono">{{
                            formatLatency(route.maximumLatencyMs)
                          }}</strong>
                        </div>
                      </div>
                    </Transition>
                  </td>
                </tr>
              </template>
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
  min-width: 860px;
}

.sort-select {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--insight-subtle);
  font-size: 11px;
  font-weight: 600;
}

.sort-select select {
  min-width: 102px;
  padding: 6px 26px 6px 9px;
  border: 1px solid var(--insight-border-strong);
  border-radius: 7px;
  color: var(--insight-ink);
  background: var(--insight-surface);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
  transition:
    border-color 140ms ease,
    box-shadow 140ms ease;
}

.sort-select select:hover,
.sort-select select:focus {
  border-color: var(--insight-primary);
  box-shadow: 0 0 0 3px var(--insight-primary-soft);
  outline: 0;
}

.rank-column {
  width: 42px;
  color: var(--insight-subtle);
  text-align: center !important;
}

.plugin-expand {
  display: inline-grid;
  width: 24px;
  height: 24px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 5px;
  color: var(--insight-subtle);
  background: transparent;
  cursor: pointer;
  transition:
    color 140ms ease,
    background-color 140ms ease,
    transform 180ms ease;
}

.plugin-expand:hover,
.plugin-expand:focus-visible {
  color: var(--insight-primary);
  background: var(--insight-primary-soft);
}

.plugin-expand svg {
  width: 16px;
  height: 16px;
}

.plugin-expand--open {
  color: var(--insight-primary);
  transform: rotate(180deg);
}

.table-primary-value {
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.plugin-detail-row td {
  padding: 0 15px 12px 57px;
  border-bottom: 1px solid var(--insight-border);
  background: var(--insight-surface-muted);
}

.plugin-detail-grid {
  display: grid;
  gap: 8px 20px;
  padding: 11px 12px;
  border-left: 2px solid var(--insight-primary);
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.plugin-detail-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.plugin-detail-item span {
  color: var(--insight-muted);
  font-size: 10px;
}

.plugin-detail-item strong {
  overflow: hidden;
  color: var(--insight-ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-detail-enter-active,
.plugin-detail-leave-active {
  transition:
    opacity 150ms ease,
    transform 150ms ease;
}

.plugin-detail-enter-from,
.plugin-detail-leave-to {
  opacity: 0;
  transform: translateY(-4px);
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
  background: #e5e7eb;
}

.impact-cell__bar i {
  display: block;
  max-width: 100%;
  height: 100%;
  background: var(--insight-primary);
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

.resource-note {
  margin: 8px 14px 0;
  color: var(--insight-muted);
  font-size: 10px;
  line-height: 1.5;
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
  min-width: 760px;
}

.route-detail-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.route-sort-marker {
  margin-left: 2px;
  color: var(--insight-primary);
  font-weight: 750;
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
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  color: var(--insight-primary);
  background: var(--insight-primary-soft);
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

  .plugin-detail-row td {
    padding-left: 15px;
  }

  .plugin-detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .route-detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
