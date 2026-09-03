<script setup lang="ts">
import { VAlert, VButton } from '@halo-dev/components'
import { computed } from 'vue'
import RiCheckboxCircleLine from '~icons/ri/checkbox-circle-line'
import RiNotification3Line from '~icons/ri/notification-3-line'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { INSIGHT_MANAGE_PERMISSION } from '@/constants/permissions'
import type { AlertIncident, AlertStatus, SelectOption } from '@/types/insight'
import { formatDateTime, formatRelativeTime, severityTone } from '@/utils/format'

const props = defineProps<{
  alerts?: AlertIncident[]
  status: AlertStatus
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  error?: string
  testingNotifications: boolean
  acknowledgingIds: Set<string>
}>()

const emit = defineEmits<{
  retry: []
  testNotification: []
  acknowledge: [id: string]
  'update:status': [value: AlertStatus]
  'load-more': []
}>()

const statusOptions: SelectOption<string>[] = [
  { label: '活动', value: 'open' },
  { label: '已确认', value: 'acknowledged' },
  { label: '已恢复', value: 'resolved' },
  { label: '全部', value: 'all' },
]

const statusModel = computed({
  get: () => props.status,
  set: (value: string) => emit('update:status', value as AlertStatus),
})

const summary = computed(() => {
  const alerts = props.alerts ?? []
  const active = alerts.filter((alert) => alert.status.toUpperCase() === 'OPEN').length
  const critical = alerts.filter((alert) => alert.severity.toLowerCase() === 'critical').length
  const warning = alerts.filter((alert) => alert.severity.toLowerCase() === 'warning').length
  return { active, critical, warning }
})

function statusLabel(status: string): string {
  const normalized = status.toUpperCase()
  if (normalized === 'ACKNOWLEDGED') return '已确认'
  if (normalized === 'RESOLVED') return '已恢复'
  return '活动'
}
</script>

<template>
  <div class="alerts-panel">
    <div v-if="alerts" class="alert-summary" aria-label="告警统计">
      <div>
        <span>活动</span><strong class="insight-mono">{{ summary.active }}</strong>
      </div>
      <div>
        <span>严重</span><strong class="insight-mono critical-text">{{ summary.critical }}</strong>
      </div>
      <div>
        <span>警告</span><strong class="insight-mono warning-text">{{ summary.warning }}</strong>
      </div>
    </div>

    <section class="insight-section">
      <SectionHeading title="告警事件" description="触发、确认和恢复状态">
        <template #actions>
          <VButton
            v-permission="[INSIGHT_MANAGE_PERMISSION]"
            size="sm"
            :loading="testingNotifications"
            @click="emit('testNotification')"
          >
            <template #icon><RiNotification3Line /></template>
            测试通知
          </VButton>
          <SegmentedControl
            v-model="statusModel"
            :options="statusOptions"
            control-label="告警状态"
            compact
          />
        </template>
      </SectionHeading>
      <SectionState
        :loading="loading && !alerts"
        :error="!alerts ? error : undefined"
        :empty="!loading && !error && !alerts?.length"
        :empty-title="status === 'open' ? '当前没有活动告警' : '该状态下没有告警记录'"
        @retry="emit('retry')"
      >
        <VAlert
          v-if="error && alerts"
          class="insight-refresh-error"
          type="error"
          :title="`告警刷新失败：${error}`"
          :closable="false"
        />
        <div v-if="alerts" class="alert-list">
          <article
            v-for="alert in alerts"
            :key="alert.id"
            class="alert-row"
            :class="`alert-row--${severityTone(alert.severity)}`"
          >
            <div class="alert-row__main">
              <div class="alert-row__title">
                <StatusBadge :tone="severityTone(alert.severity)" />
                <strong>{{ alert.title }}</strong>
              </div>
              <p>{{ alert.summary }}</p>
              <div class="alert-row__meta">
                <span>{{ alert.ruleId }}</span>
                <time :datetime="new Date(alert.triggeredAtMs).toISOString()">{{
                  formatDateTime(alert.triggeredAtMs)
                }}</time>
                <span>{{ formatRelativeTime(alert.updatedAtMs) }}</span>
                <span v-if="alert.occurrenceCount > 1" class="insight-mono"
                  >×{{ alert.occurrenceCount }}</span
                >
              </div>
            </div>
            <div class="alert-row__measure">
              <template v-if="alert.lastValue != null">
                <span>当前值</span>
                <strong class="insight-mono">{{ alert.lastValue }}</strong>
                <small v-if="alert.thresholdValue != null" class="insight-mono"
                  >阈值 {{ alert.thresholdValue }}</small
                >
              </template>
            </div>
            <div class="alert-row__action">
              <VButton
                v-if="alert.status.toUpperCase() === 'OPEN'"
                v-permission="[INSIGHT_MANAGE_PERMISSION]"
                size="sm"
                :loading="acknowledgingIds.has(alert.id)"
                @click="emit('acknowledge', alert.id)"
              >
                <template #icon><RiCheckboxCircleLine /></template>
                确认
              </VButton>
              <StatusBadge
                v-else
                :tone="alert.status.toUpperCase() === 'RESOLVED' ? 'healthy' : 'info'"
                :label="statusLabel(alert.status)"
              />
            </div>
          </article>
        </div>
        <div v-if="hasMore" class="timeline-more">
          <VButton size="sm" :loading="loadingMore" @click="emit('load-more')">
            加载更多历史
          </VButton>
        </div>
      </SectionState>
    </section>
  </div>
</template>

<style scoped>
.alerts-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.alert-summary {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.alert-summary > div {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-right: 1px solid var(--insight-border);
}

.alert-summary > div:last-child {
  border-right: 0;
}

.alert-summary span {
  color: var(--insight-muted);
  font-size: 12px;
}

.alert-summary strong {
  font-size: 24px;
  line-height: 30px;
}

.critical-text {
  color: var(--insight-red);
}

.warning-text {
  color: var(--insight-amber);
}

.alert-list {
  display: flex;
  flex-direction: column;
}

.alert-row {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 104px;
  align-items: center;
  grid-template-columns: minmax(0, 1fr) minmax(110px, auto) 94px;
  gap: 18px;
  padding: 14px 16px 14px 19px;
  border-bottom: 1px solid var(--insight-border);
}

.alert-row:last-child {
  border-bottom: 0;
}

.alert-row::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: var(--insight-cyan);
  content: '';
}

.alert-row--warning::before {
  background: var(--insight-amber);
}

.alert-row--critical::before {
  background: var(--insight-red);
}

.alert-row__main {
  min-width: 0;
}

.alert-row__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}

.alert-row__title strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-row p {
  margin: 6px 0;
  overflow: hidden;
  color: var(--insight-muted);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-row__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  color: var(--insight-subtle);
  font-size: 10px;
}

.alert-row__measure {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.alert-row__measure span,
.alert-row__measure small {
  color: var(--insight-muted);
  font-size: 10px;
}

.alert-row__measure strong {
  margin: 2px 0;
  font-size: 15px;
}

.alert-row__action {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .alert-row {
    align-items: flex-start;
    grid-template-columns: 1fr auto;
  }

  .alert-row__measure {
    display: none;
  }
}

@media (max-width: 560px) {
  .alert-summary {
    grid-template-columns: 1fr;
  }

  .alert-summary > div {
    min-height: 54px;
    border-right: 0;
    border-bottom: 1px solid var(--insight-border);
  }

  .alert-summary > div:last-child {
    border-bottom: 0;
  }

  .alert-row {
    grid-template-columns: 1fr;
  }

  .alert-row__action {
    justify-content: flex-start;
  }
}
</style>
