<script setup lang="ts">
import { Dialog, VAlert, VButton, VPageHeader } from '@halo-dev/components'
import { computed, ref, type Component } from 'vue'
import RiAlarmWarningLine from '~icons/ri/alarm-warning-line'
import RiDashboardLine from '~icons/ri/dashboard-line'
import RiDownload2Line from '~icons/ri/download-2-line'
import RiLineChartLine from '~icons/ri/line-chart-line'
import RiPulseLine from '~icons/ri/pulse-line'
import RiRadarLine from '~icons/ri/radar-line'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiTimeLine from '~icons/ri/time-line'

import SegmentedControl from '@/components/common/SegmentedControl.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import HealthRail from '@/components/dashboard/HealthRail.vue'
import OptimizationModal from '@/components/optimization/OptimizationModal.vue'
import { useInsightDashboard } from '@/composables/useInsightDashboard'
import { REQUEST_HEALTH_THRESHOLDS } from '@/constants/monitoring'
import { INSIGHT_MANAGE_PERMISSION } from '@/constants/permissions'
import type {
  DashboardTab,
  HealthRailItem,
  HealthStatus,
  PluginConsumerItem,
  TimeRange,
} from '@/types/insight'
import { DASHBOARD_TAB_OPTIONS, TIME_RANGE_OPTIONS } from '@/types/insight'
import {
  formatBytes,
  formatDateTime,
  formatDurationMs,
  formatLatency,
  formatNumber,
  formatPercent,
} from '@/utils/format'
import { summarizeAlerts } from '@/utils/alertStatus'
import { resolveCollectorSignal } from '@/utils/collectorHealth'
import { resolveRuntimeHealth } from '@/utils/overviewHealth'
import AlertsPanel from '@/views/panels/AlertsPanel.vue'
import EventsPanel from '@/views/panels/EventsPanel.vue'
import OverviewPanel from '@/views/panels/OverviewPanel.vue'
import PerformancePanel from '@/views/panels/PerformancePanel.vue'
import DiagnosticsPanel from '@/views/panels/DiagnosticsPanel.vue'

import '@/styles/insight.css'

const activeTab = ref<DashboardTab>('overview')
const range = ref<TimeRange>('1h')

const {
  overview,
  trends,
  plugins,
  consumers,
  routes,
  alerts,
  events,
  optimization,
  pluginSort,
  routeSort,
  alertStatus,
  eventFilters,
  lastUpdatedAt,
  refreshing,
  exporting,
  testingNotifications,
  clearingData,
  loadingMoreEvents,
  loadingMoreAlerts,
  hasMoreAlerts,
  acknowledgingAlertIds,
  applyingOptimization,
  rollingBackOptimization,
  disablingPluginId,
  refresh,
  loadMoreEvents,
  loadMoreAlerts,
  acknowledgeAlert,
  sendTestNotification,
  clearMonitoringData,
  exportData,
  applyOptimization,
  rollbackOptimization,
  disablePlugin,
} = useInsightDashboard(activeTab, range)

const overviewData = overview.data
const overviewLoading = overview.loading
const overviewError = overview.error
const trendsData = trends.data
const trendsLoading = trends.loading
const trendsError = trends.error
const pluginsData = plugins.data
const pluginsLoading = plugins.loading
const pluginsError = plugins.error
const consumersData = consumers.data
const consumersLoading = consumers.loading
const consumersError = consumers.error
const routesData = routes.data
const routesLoading = routes.loading
const routesError = routes.error
const alertsData = alerts.data
const alertsLoading = alerts.loading
const alertsError = alerts.error
const eventsData = events.data
const eventsLoading = events.loading
const eventsError = events.error
const optimizationData = optimization.data
const optimizationLoading = optimization.loading
const optimizationError = optimization.error
const optimizationVisible = ref(false)

const activeAlertSummary = computed(() => summarizeAlerts(overviewData.value?.activeAlerts ?? []))
const collectorSignal = computed(() => resolveCollectorSignal(overviewData.value))
const supportsHistoricalRange = computed(
  () => activeTab.value === 'overview' || activeTab.value === 'performance',
)
const selectedRangeLabel = computed(
  () => TIME_RANGE_OPTIONS.find((option) => option.value === range.value)?.label ?? range.value,
)
const historicalRangeLoading = computed(
  () =>
    trendsLoading.value ||
    (activeTab.value === 'performance' &&
      (pluginsLoading.value || consumersLoading.value || routesLoading.value)),
)
const historicalRangeFailed = computed(
  () =>
    trendsData.value?.range !== range.value &&
    Boolean(
      trendsError.value ||
      (activeTab.value === 'performance' &&
        (pluginsError.value || consumersError.value || routesError.value)),
    ),
)
const historicalRangeStatus = computed(() => {
  if (historicalRangeLoading.value) return `${selectedRangeLabel.value} · 正在载入`
  if (historicalRangeFailed.value) return `${selectedRangeLabel.value} · 切换失败`
  if (trendsData.value?.range === range.value) {
    return `${selectedRangeLabel.value} · 已应用`
  }
  return `${selectedRangeLabel.value} · 等待数据`
})

const tabIcons: Record<DashboardTab, Component> = {
  overview: RiDashboardLine,
  performance: RiLineChartLine,
  alerts: RiAlarmWarningLine,
  events: RiTimeLine,
  diagnostics: RiRadarLine,
}

function runtimeHealthExplanation(
  data: NonNullable<typeof overviewData.value>,
  status: HealthStatus,
): string {
  const reasons: string[] = []
  if (!data.runtime.running) reasons.push('Halo 运行状态未运行')
  if (data.runtime.lastCollectionError)
    reasons.push(`采集异常：${data.runtime.lastCollectionError}`)
  if (data.jvm.deadlockedThreads > 0) {
    reasons.push(`检测到 ${data.jvm.deadlockedThreads} 个死锁线程`)
  }
  data.dependencies
    .filter((item) => item.status.toLowerCase() === 'critical')
    .slice(0, 3)
    .forEach((item) => reasons.push(`${item.name}：${item.message}`))
  const activeCriticalAlerts = data.activeAlerts.filter(
    (alert) => alert.severity.toLowerCase() === 'critical',
  )
  if (activeCriticalAlerts.length > 0) {
    const acknowledgedCritical = activeCriticalAlerts.filter(
      (alert) => alert.status.toUpperCase() === 'ACKNOWLEDGED',
    ).length
    reasons.push(
      `存在 ${activeCriticalAlerts.length} 条未恢复严重告警${acknowledgedCritical ? `，已确认 ${acknowledgedCritical} 条` : ''}`,
    )
  }
  if (reasons.length) return reasons.join('；')
  if (status === 'warning') return '存在需要关注的运行状态或后台任务'
  if (status === 'unknown') return '等待运行时监控样本'
  return '运行时、采集任务和依赖状态均正常'
}

function requestHealthExplanation(
  data: NonNullable<typeof overviewData.value>,
  status: HealthStatus,
): string {
  const reasons: string[] = []
  if (data.requests.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.criticalErrorRatePercent) {
    reasons.push(
      `错误率 ${formatPercent(data.requests.errorRatePercent)} ≥ 严重阈值 ${formatPercent(REQUEST_HEALTH_THRESHOLDS.criticalErrorRatePercent)}`,
    )
  } else if (data.requests.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.warningErrorRatePercent) {
    reasons.push(
      `错误率 ${formatPercent(data.requests.errorRatePercent)} ≥ 关注阈值 ${formatPercent(REQUEST_HEALTH_THRESHOLDS.warningErrorRatePercent)}`,
    )
  }
  if (data.requests.p95Ms >= REQUEST_HEALTH_THRESHOLDS.criticalP95Ms) {
    reasons.push(
      `P95 ${formatLatency(data.requests.p95Ms)} ≥ 严重阈值 ${formatLatency(REQUEST_HEALTH_THRESHOLDS.criticalP95Ms)}`,
    )
  } else if (data.requests.p95Ms >= REQUEST_HEALTH_THRESHOLDS.warningP95Ms) {
    reasons.push(
      `P95 ${formatLatency(data.requests.p95Ms)} ≥ 关注阈值 ${formatLatency(REQUEST_HEALTH_THRESHOLDS.warningP95Ms)}`,
    )
  }
  if (reasons.length) return reasons.join('；')
  if (status === 'unknown') return '等待请求样本'
  return '错误率和 P95 延迟均在当前阈值内'
}

const healthItems = computed<HealthRailItem[]>(() => {
  const data = overviewData.value
  if (!data) {
    return [
      {
        key: 'runtime',
        label: '运行时',
        status: 'unknown',
        value: '—',
        detail: '等待运行指标',
        explanation: '等待运行时监控样本',
      },
      {
        key: 'storage',
        label: '监控存储',
        status: 'unknown',
        value: '—',
        detail: '等待存储状态',
        explanation: '等待监控存储状态',
      },
      {
        key: 'requests',
        label: '请求',
        status: 'unknown',
        value: '—',
        detail: '等待请求样本',
        explanation: '等待请求样本',
      },
      {
        key: 'alerts',
        label: '待处理告警',
        status: 'unknown',
        value: '—',
        detail: '等待告警状态',
        explanation: '等待告警状态',
      },
    ]
  }

  const alertSummary = summarizeAlerts(data.activeAlerts)
  const requestWindowSeconds = Math.max(
    (data.requests.windowToMs - data.requests.windowFromMs) / 1000,
    1,
  )
  const runtimeStatus = resolveRuntimeHealth(
    data.health,
    data.runtime.running,
    data.jvm.deadlockedThreads,
  )
  const storageStatus: HealthStatus = !data.storage.available
    ? 'critical'
    : data.storage.writePaused
      ? 'warning'
      : 'healthy'
  const requestStatus: HealthStatus =
    data.requests.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.criticalErrorRatePercent ||
    data.requests.p95Ms >= REQUEST_HEALTH_THRESHOLDS.criticalP95Ms
      ? 'critical'
      : data.requests.errorRatePercent >= REQUEST_HEALTH_THRESHOLDS.warningErrorRatePercent ||
          data.requests.p95Ms >= REQUEST_HEALTH_THRESHOLDS.warningP95Ms
        ? 'warning'
        : 'healthy'
  const alertHealth: HealthStatus =
    alertSummary.pendingCritical > 0
      ? 'critical'
      : alertSummary.pending.length > 0
        ? 'warning'
        : 'healthy'

  return [
    {
      key: 'runtime',
      label: '运行时',
      status: runtimeStatus,
      value: formatDurationMs(data.runtime.uptimeMs),
      detail: `Halo ${data.runtime.haloVersion} · Java ${data.runtime.javaVersion}`,
      explanation: runtimeHealthExplanation(data, runtimeStatus),
    },
    {
      key: 'storage',
      label: '监控存储',
      status: storageStatus,
      value: formatBytes(data.storage.sizeBytes),
      detail: `${data.storage.message} · 上限 ${formatBytes(data.storage.maxSizeBytes)}`,
      explanation: !data.storage.available
        ? `监控存储不可用：${data.storage.message}`
        : data.storage.writePaused
          ? `历史写入已暂停：${data.storage.message}`
          : '监控存储可用，历史写入正常',
    },
    {
      key: 'requests',
      label: '请求',
      status: requestStatus,
      value: `${formatNumber(data.requests.requestCount / requestWindowSeconds)} req/s`,
      detail: `P95 ${formatLatency(data.requests.p95Ms)} · 错误 ${formatPercent(data.requests.errorRatePercent)}`,
      explanation: requestHealthExplanation(data, requestStatus),
    },
    {
      key: 'alerts',
      label: '待处理告警',
      status: alertHealth,
      value: `${alertSummary.pending.length} 条`,
      detail:
        alertSummary.pending.length > 0
          ? `${alertSummary.pendingCritical} 严重 · ${alertSummary.pendingWarning} 警告${alertSummary.acknowledged.length ? ` · 已确认 ${alertSummary.acknowledged.length} 条` : ''}`
          : alertSummary.acknowledged.length > 0
            ? `已确认 ${alertSummary.acknowledged.length} 条，持续观察`
            : '所有规则均已恢复',
      explanation:
        alertSummary.pendingCritical > 0
          ? `存在 ${alertSummary.pendingCritical} 条待处理严重告警`
          : alertSummary.pending.length > 0
            ? `存在 ${alertSummary.pending.length} 条待处理告警，需要关注`
            : alertSummary.acknowledged.length > 0
              ? `没有待处理告警；已确认 ${alertSummary.acknowledged.length} 条，故障恢复前继续观察，不重复提醒`
              : '当前没有待处理或持续观察中的告警',
    },
  ]
})

const updatedLabel = computed(() => {
  const value = overviewData.value?.runtime.lastCollectedAtMs
  if (value) return `最新样本 ${formatDateTime(value)}`
  if (lastUpdatedAt.value)
    return `刷新于 ${formatDateTime(new Date(lastUpdatedAt.value).toISOString())}`
  return '30 秒自动刷新'
})

function selectTab(tab: DashboardTab) {
  activeTab.value = tab
}

function openPerformanceDiagnosis() {
  optimizationVisible.value = true
  void refresh(true)
}

function confirmDisablePlugin(consumer: PluginConsumerItem) {
  Dialog.warning({
    uniqueId: `insight-disable-plugin-${consumer.pluginId}`,
    title: `停用 ${consumer.displayName || consumer.pluginId}`,
    description:
      '停用插件可能使对应站点功能暂时不可用。确认后 Halo 会停用该插件；请观察至少 5 分钟的 JVM、CPU、GC 与请求趋势，再决定是否重新启用。',
    confirmText: '停用并观察',
    confirmType: 'danger',
    showCancel: true,
    onConfirm: () =>
      void disablePlugin(consumer.pluginId, consumer.displayName || consumer.pluginId),
  })
}

function confirmClearMonitoringData() {
  Dialog.warning({
    uniqueId: 'insight-clear-monitoring-data',
    title: '清空监控历史',
    description: '将永久删除全部监控历史数据。此操作无法撤销，但实时采集不会停止。',
    confirmText: '确认清空',
    confirmType: 'danger',
    onConfirm: clearMonitoringData,
  })
}
</script>

<template>
  <div class="insight-shell">
    <VPageHeader title="运行洞察">
      <template #icon>
        <RiRadarLine class="page-title-icon" />
      </template>
      <template #actions>
        <div class="page-actions">
          <div class="collection-state" :title="collectorSignal.message">
            <StatusBadge :tone="collectorSignal.status" :label="collectorSignal.label" animate />
            <span class="updated-at insight-mono">{{ updatedLabel }}</span>
          </div>
          <VButton type="primary" size="sm" :loading="refreshing" @click="refresh()">
            <template #icon><RiRefreshLine /></template>
            {{ refreshing ? '刷新中' : '刷新' }}
          </VButton>
          <VButton size="sm" @click="openPerformanceDiagnosis">
            <template #icon><RiPulseLine /></template>
            性能诊断
          </VButton>
          <VButton
            v-permission="[INSIGHT_MANAGE_PERMISSION]"
            size="sm"
            :loading="exporting"
            @click="exportData"
          >
            <template #icon><RiDownload2Line /></template>
            {{ exporting ? '导出中' : '导出' }}
          </VButton>
        </div>
      </template>
    </VPageHeader>

    <main class="insight-content">
      <VAlert
        v-if="overviewError && overviewData"
        type="error"
        :title="`最新运行状态刷新失败，当前显示上一次成功采样的数据：${overviewError}`"
        :closable="false"
      />

      <HealthRail :items="healthItems" />

      <div class="monitor-toolbar">
        <nav class="monitor-tabs" aria-label="监控视图">
          <button
            v-for="tab in DASHBOARD_TAB_OPTIONS"
            :key="tab.value"
            type="button"
            class="monitor-tabs__item"
            :class="{ 'monitor-tabs__item--active': activeTab === tab.value }"
            :aria-current="activeTab === tab.value ? 'page' : undefined"
            @click="selectTab(tab.value)"
          >
            <component :is="tabIcons[tab.value]" aria-hidden="true" />
            <span>{{ tab.label }}</span>
            <span
              v-if="tab.value === 'alerts' && activeAlertSummary.pending.length"
              class="tab-count insight-mono"
            >
              {{ activeAlertSummary.pending.length }}
            </span>
          </button>
        </nav>
        <div v-if="supportsHistoricalRange" class="range-control">
          <div class="range-control__copy">
            <span>历史统计范围</span>
            <small
              aria-live="polite"
              :class="{ 'range-control__status--error': historicalRangeFailed }"
            >
              {{ historicalRangeStatus }}
            </small>
          </div>
          <SegmentedControl
            v-model="range"
            :options="TIME_RANGE_OPTIONS"
            control-label="监控时间范围"
            compact
          />
        </div>
      </div>

      <OverviewPanel
        v-if="activeTab === 'overview'"
        :overview="overviewData"
        :trends="trendsData"
        :loading="overviewLoading"
        :error="overviewError"
        :trends-loading="trendsLoading"
        :trends-error="trendsError"
        :clearing-data="clearingData"
        @retry="refresh()"
        @clear-monitoring-data="confirmClearMonitoringData"
      />

      <PerformancePanel
        v-else-if="activeTab === 'performance'"
        :plugins="pluginsData"
        :consumers="consumersData"
        :routes="routesData"
        :plugin-sort="pluginSort"
        :route-sort="routeSort"
        :plugins-loading="pluginsLoading"
        :consumers-loading="consumersLoading"
        :routes-loading="routesLoading"
        :plugins-error="pluginsError"
        :consumers-error="consumersError"
        :routes-error="routesError"
        @retry="refresh()"
        @update:plugin-sort="pluginSort = $event"
        @update:route-sort="routeSort = $event"
      />

      <AlertsPanel
        v-else-if="activeTab === 'alerts'"
        :alerts="alertsData"
        :active-alerts="overviewData?.activeAlerts"
        :status="alertStatus"
        :loading="alertsLoading"
        :error="alertsError"
        :testing-notifications="testingNotifications"
        :acknowledging-ids="acknowledgingAlertIds"
        :loading-more="loadingMoreAlerts"
        :has-more="hasMoreAlerts"
        @retry="refresh()"
        @test-notification="sendTestNotification"
        @acknowledge="acknowledgeAlert"
        @update:status="alertStatus = $event"
        @load-more="loadMoreAlerts"
      />

      <EventsPanel
        v-else-if="activeTab === 'events'"
        :events="eventsData"
        :loading="eventsLoading"
        :loading-more="loadingMoreEvents"
        :error="eventsError"
        :event-type="eventFilters.type"
        :severity="eventFilters.severity"
        @retry="refresh()"
        @load-more="loadMoreEvents"
        @update:event-type="eventFilters.type = $event"
        @update:severity="eventFilters.severity = $event"
      />
      <DiagnosticsPanel v-else-if="activeTab === 'diagnostics'" />
    </main>

    <OptimizationModal
      v-if="optimizationVisible"
      :status="optimizationData"
      :loading="optimizationLoading"
      :error="optimizationError"
      :applying="applyingOptimization"
      :rolling-back="rollingBackOptimization"
      :disabling-plugin-id="disablingPluginId"
      @close="optimizationVisible = false"
      @retry="refresh()"
      @apply="applyOptimization"
      @rollback="rollbackOptimization"
      @disable-plugin="confirmDisablePlugin"
    />
  </div>
</template>

<style scoped>
.page-title-icon {
  width: 20px;
  height: 20px;
  margin-right: 8px;
  color: #1f2937;
}

.updated-at {
  color: var(--insight-muted);
  font-size: var(--insight-text-xs);
  line-height: 16px;
  white-space: nowrap;
}

.page-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.collection-state {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-right: 2px;
}

.insight-content {
  display: flex;
  max-width: 1640px;
  flex-direction: column;
  gap: 14px;
  margin: 0 auto;
  padding: 18px 18px 28px;
}

.monitor-toolbar {
  display: flex;
  min-height: 46px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 2px;
  border-bottom: 1px solid var(--insight-border);
  background: transparent;
}

.monitor-tabs {
  display: flex;
  min-width: 0;
  align-self: stretch;
  overflow-x: auto;
}

.monitor-tabs__item {
  position: relative;
  display: inline-flex;
  min-width: 88px;
  min-height: 44px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 8px 12px;
  border: 0;
  border-radius: 0;
  color: var(--insight-muted);
  background: transparent;
  font: inherit;
  font-size: 12px;
  font-weight: 620;
  cursor: pointer;
}

.monitor-tabs__item:hover {
  color: var(--insight-ink);
  background: var(--insight-surface-muted);
}

.monitor-tabs__item--active {
  color: var(--insight-primary);
  background: transparent;
}

.monitor-tabs__item--active::after {
  position: absolute;
  right: 10px;
  bottom: 0;
  left: 10px;
  height: 2px;
  background: var(--insight-primary);
  content: '';
}

.monitor-tabs__item svg {
  width: 16px;
  height: 16px;
}

.tab-count {
  display: inline-flex;
  min-width: 19px;
  height: 19px;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  border-radius: 3px;
  color: #ffffff;
  background: var(--insight-red);
  font-size: 9px;
}

.range-control {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 9px;
}

.range-control__copy {
  display: flex;
  min-width: 104px;
  flex-direction: column;
  gap: 1px;
}

.range-control__copy > span {
  color: var(--insight-subtle);
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.range-control__copy > small {
  color: var(--insight-primary);
  font-size: 9px;
  white-space: nowrap;
}

.range-control__status--error {
  color: var(--insight-red) !important;
}

@media (max-width: 920px) {
  .monitor-toolbar {
    align-items: stretch;
    flex-direction: column;
    border-bottom: 0;
    gap: 8px;
  }

  .range-control {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .range-control__copy {
    min-width: 0;
  }
}

@media (max-width: 640px) {
  .insight-content {
    padding: 12px 10px 20px;
  }

  .collection-state {
    display: none;
  }

  .monitor-tabs {
    width: 100%;
  }

  .monitor-tabs__item {
    min-width: 78px;
    flex: 1 0 auto;
  }

  .range-control {
    width: 100%;
  }
}
</style>
