<script setup lang="ts">
import { Dialog, VAlert, VButton, VPageHeader, VSpace } from '@halo-dev/components'
import { computed, ref, type Component } from 'vue'
import RiAlarmWarningLine from '~icons/ri/alarm-warning-line'
import RiDashboardLine from '~icons/ri/dashboard-line'
import RiDownload2Line from '~icons/ri/download-2-line'
import RiPulseLine from '~icons/ri/pulse-line'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiTimeLine from '~icons/ri/time-line'

import SegmentedControl from '@/components/common/SegmentedControl.vue'
import HealthRail from '@/components/dashboard/HealthRail.vue'
import { useInsightDashboard } from '@/composables/useInsightDashboard'
import { REQUEST_HEALTH_THRESHOLDS } from '@/constants/monitoring'
import { INSIGHT_MANAGE_PERMISSION } from '@/constants/permissions'
import type { DashboardTab, HealthRailItem, HealthStatus, TimeRange } from '@/types/insight'
import { DASHBOARD_TAB_OPTIONS, TIME_RANGE_OPTIONS } from '@/types/insight'
import {
  formatBytes,
  formatDateTime,
  formatDurationMs,
  formatLatency,
  formatNumber,
  formatPercent,
} from '@/utils/format'
import { resolveRuntimeHealth } from '@/utils/overviewHealth'
import AlertsPanel from '@/views/panels/AlertsPanel.vue'
import EventsPanel from '@/views/panels/EventsPanel.vue'
import OverviewPanel from '@/views/panels/OverviewPanel.vue'
import PerformancePanel from '@/views/panels/PerformancePanel.vue'

import '@/styles/insight.css'

const activeTab = ref<DashboardTab>('overview')
const range = ref<TimeRange>('1h')

const {
  overview,
  trends,
  plugins,
  routes,
  alerts,
  events,
  pluginSort,
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
  refresh,
  loadMoreEvents,
  loadMoreAlerts,
  acknowledgeAlert,
  sendTestNotification,
  clearMonitoringData,
  exportData,
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
const routesData = routes.data
const routesLoading = routes.loading
const routesError = routes.error
const alertsData = alerts.data
const alertsLoading = alerts.loading
const alertsError = alerts.error
const eventsData = events.data
const eventsLoading = events.loading
const eventsError = events.error

const tabIcons: Record<DashboardTab, Component> = {
  overview: RiDashboardLine,
  performance: RiPulseLine,
  alerts: RiAlarmWarningLine,
  events: RiTimeLine,
}

const healthItems = computed<HealthRailItem[]>(() => {
  const data = overviewData.value
  if (!data) {
    return [
      { key: 'runtime', label: '运行时', status: 'unknown', value: '—', detail: '等待运行指标' },
      { key: 'storage', label: '监控存储', status: 'unknown', value: '—', detail: '等待存储状态' },
      { key: 'requests', label: '请求', status: 'unknown', value: '—', detail: '等待请求样本' },
      { key: 'alerts', label: '当前告警', status: 'unknown', value: '—', detail: '等待告警状态' },
    ]
  }

  const criticalAlerts = data.activeAlerts.filter(
    (alert) => alert.severity.toLowerCase() === 'critical',
  ).length
  const warningAlerts = data.activeAlerts.filter(
    (alert) => alert.severity.toLowerCase() === 'warning',
  ).length
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
    criticalAlerts > 0 ? 'critical' : data.activeAlerts.length > 0 ? 'warning' : 'healthy'

  return [
    {
      key: 'runtime',
      label: '运行时',
      status: runtimeStatus,
      value: formatDurationMs(data.runtime.uptimeMs),
      detail: `Halo ${data.runtime.haloVersion} · Java ${data.runtime.javaVersion}`,
    },
    {
      key: 'storage',
      label: '监控存储',
      status: storageStatus,
      value: formatBytes(data.storage.sizeBytes),
      detail: `${data.storage.message} · 上限 ${formatBytes(data.storage.maxSizeBytes)}`,
    },
    {
      key: 'requests',
      label: '请求',
      status: requestStatus,
      value: `${formatNumber(data.requests.requestCount / requestWindowSeconds)} req/s`,
      detail: `P95 ${formatLatency(data.requests.p95Ms)} · 错误 ${formatPercent(data.requests.errorRatePercent)}`,
    },
    {
      key: 'alerts',
      label: '当前告警',
      status: alertHealth,
      value: `${data.activeAlerts.length} 条`,
      detail: `${criticalAlerts} 严重 · ${warningAlerts} 警告`,
    },
  ]
})

const updatedLabel = computed(() => {
  const value = overviewData.value?.generatedAtMs
  if (value) return `采样于 ${formatDateTime(value)}`
  if (lastUpdatedAt.value)
    return `刷新于 ${formatDateTime(new Date(lastUpdatedAt.value).toISOString())}`
  return '30 秒自动刷新'
})

function selectTab(tab: DashboardTab) {
  activeTab.value = tab
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
    <VPageHeader title="运行监控">
      <template #icon>
        <RiPulseLine class="page-title-icon" />
      </template>
      <template #actions>
        <VSpace class="page-actions">
          <span class="updated-at insight-mono">{{ updatedLabel }}</span>
          <VButton type="secondary" :loading="refreshing" @click="refresh()">
            <template #icon><RiRefreshLine /></template>
            刷新
          </VButton>
          <VButton
            v-permission="[INSIGHT_MANAGE_PERMISSION]"
            :loading="exporting"
            @click="exportData"
          >
            <template #icon><RiDownload2Line /></template>
            导出
          </VButton>
        </VSpace>
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
              v-if="tab.value === 'alerts' && overviewData?.activeAlerts.length"
              class="tab-count insight-mono"
            >
              {{ overviewData.activeAlerts.length }}
            </span>
          </button>
        </nav>
        <SegmentedControl
          v-model="range"
          :options="TIME_RANGE_OPTIONS"
          control-label="监控时间范围"
          compact
        />
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
        :routes="routesData"
        :plugin-sort="pluginSort"
        :plugins-loading="pluginsLoading"
        :routes-loading="routesLoading"
        :plugins-error="pluginsError"
        :routes-error="routesError"
        @retry="refresh()"
        @update:plugin-sort="pluginSort = $event"
      />

      <AlertsPanel
        v-else-if="activeTab === 'alerts'"
        :alerts="alertsData"
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
        v-else
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
    </main>
  </div>
</template>

<style scoped>
.page-title-icon {
  width: 20px;
  height: 20px;
  margin-right: 8px;
  align-self: center;
  color: var(--insight-cyan);
}

.page-actions {
  flex-wrap: wrap;
}

.updated-at {
  align-self: center;
  color: var(--insight-muted);
  font-size: 10px;
  white-space: nowrap;
}

.insight-content {
  display: flex;
  max-width: 1680px;
  flex-direction: column;
  gap: 14px;
  margin: 0 auto;
  padding: 16px;
}

.monitor-toolbar {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 6px;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
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
  min-width: 94px;
  min-height: 36px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 7px 12px;
  border: 0;
  border-radius: 4px;
  color: var(--insight-muted);
  background: transparent;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.monitor-tabs__item:hover {
  color: var(--insight-ink);
  background: var(--insight-surface-muted);
}

.monitor-tabs__item--active {
  color: var(--insight-ink);
  background: #eef4f5;
}

.monitor-tabs__item--active::after {
  position: absolute;
  right: 10px;
  bottom: 0;
  left: 10px;
  height: 2px;
  background: var(--insight-cyan);
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
  border-radius: 9px;
  color: #ffffff;
  background: var(--insight-red);
  font-size: 9px;
}

@media (max-width: 920px) {
  .monitor-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .insight-content {
    padding: 10px;
  }

  .monitor-tabs {
    width: 100%;
  }

  .monitor-tabs__item {
    min-width: 78px;
    flex: 1 0 auto;
  }

  .updated-at {
    width: 100%;
    order: 3;
  }
}
</style>
