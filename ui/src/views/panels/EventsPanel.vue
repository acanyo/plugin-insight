<script setup lang="ts">
import { VAlert, VButton } from '@halo-dev/components'
import { computed } from 'vue'
import RiArrowDownSLine from '~icons/ri/arrow-down-s-line'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import type { CursorPage, MonitorEvent, Severity } from '@/types/insight'
import {
  formatRelativeTime,
  formatTime,
  formatTimelineDay,
  severityTone,
  timelineDayKey,
} from '@/utils/format'

const props = defineProps<{
  events?: CursorPage<MonitorEvent>
  loading: boolean
  loadingMore: boolean
  error?: string
  eventType: string
  severity: Severity | 'all'
}>()

const eventGroups = computed(() => {
  const groups: Array<{ key: string; label: string; events: MonitorEvent[] }> = []
  const orderedEvents = [...(props.events?.items ?? [])].sort(
    (left, right) => right.occurredAtMs - left.occurredAtMs,
  )
  for (const event of orderedEvents) {
    const key = timelineDayKey(event.occurredAtMs)
    const current = groups[groups.length - 1]
    if (current?.key === key) {
      current.events.push(event)
    } else {
      groups.push({ key, label: formatTimelineDay(event.occurredAtMs), events: [event] })
    }
  }
  return groups
})

const eventTypeLabels: Record<string, string> = {
  lifecycle: '生命周期',
  plugin: '插件',
  configuration: '配置',
  alert: '告警',
  request: '请求异常',
  runtime: 'Halo 状态',
  collector: '指标采集',
  retention: '历史清理',
  optimization: '性能优化',
  storage: '存储',
  data: '数据维护',
}

function eventTypeLabel(type: string): string {
  return eventTypeLabels[type.toLowerCase()] ?? type
}

const emit = defineEmits<{
  retry: []
  loadMore: []
  'update:eventType': [value: string]
  'update:severity': [value: Severity | 'all']
}>()
</script>

<template>
  <section class="insight-section">
    <SectionHeading title="事件时间线" description="Halo、插件、配置、异常与告警状态变化">
      <template #actions>
        <label class="select-control">
          <span class="sr-only">事件类型</span>
          <select
            :value="eventType"
            @change="emit('update:eventType', ($event.target as HTMLSelectElement).value)"
          >
            <option value="all">全部类型</option>
            <option value="lifecycle">生命周期</option>
            <option value="plugin">插件</option>
            <option value="configuration">配置</option>
            <option value="alert">告警</option>
            <option value="request">请求异常</option>
            <option value="runtime">Halo 状态刷新</option>
            <option value="collector">指标采集</option>
            <option value="retention">历史清理</option>
            <option value="optimization">性能优化</option>
            <option value="storage">存储</option>
            <option value="data">数据维护</option>
          </select>
          <RiArrowDownSLine aria-hidden="true" />
        </label>
        <label class="select-control">
          <span class="sr-only">严重程度</span>
          <select
            :value="severity"
            @change="
              emit(
                'update:severity',
                ($event.target as HTMLSelectElement).value as Severity | 'all',
              )
            "
          >
            <option value="all">全部级别</option>
            <option value="critical">严重</option>
            <option value="warning">警告</option>
            <option value="info">信息</option>
          </select>
          <RiArrowDownSLine aria-hidden="true" />
        </label>
      </template>
    </SectionHeading>
    <SectionState
      :loading="loading && !events"
      :error="!events ? error : undefined"
      :empty="!loading && !error && !events?.items.length"
      empty-title="该筛选条件下没有事件"
      @retry="emit('retry')"
    >
      <VAlert
        v-if="error && events"
        class="insight-refresh-error"
        type="error"
        :title="`事件刷新失败：${error}`"
        :closable="false"
      />
      <div v-if="events" class="timeline">
        <section v-for="group in eventGroups" :key="group.key" class="timeline-group">
          <h2 class="timeline-group__heading">{{ group.label }}</h2>
          <article v-for="event in group.events" :key="event.id" class="timeline-item">
            <div
              class="timeline-item__marker"
              :class="`timeline-item__marker--${severityTone(event.severity)}`"
              aria-hidden="true"
            ></div>
            <time
              class="timeline-item__time insight-mono"
              :datetime="new Date(event.occurredAtMs).toISOString()"
              :title="new Date(event.occurredAtMs).toLocaleString('zh-CN', { hour12: false })"
            >
              {{ formatTime(event.occurredAtMs) }}
            </time>
            <div class="timeline-item__body">
              <div class="timeline-item__title">
                <StatusBadge :tone="severityTone(event.severity)" />
                <strong>{{ event.title }}</strong>
                <span v-if="event.occurrenceCount > 1" class="repeat-count insight-mono"
                  >×{{ event.occurrenceCount }}</span
                >
              </div>
              <p>{{ event.summary }}</p>
              <div class="timeline-item__meta">
                <span>{{ event.source }}</span>
                <span>{{ eventTypeLabel(event.type) }}</span>
                <span>{{ formatRelativeTime(event.lastSeenAtMs) }}</span>
              </div>
            </div>
          </article>
        </section>
        <div v-if="events.nextCursor" class="timeline-more">
          <VButton :loading="loadingMore" @click="emit('loadMore')">加载更早事件</VButton>
        </div>
      </div>
    </SectionState>
  </section>
</template>

<style scoped>
.select-control {
  position: relative;
  display: inline-flex;
  min-width: 108px;
  height: 32px;
  align-items: center;
}

.select-control select {
  width: 100%;
  height: 100%;
  appearance: none;
  padding: 0 28px 0 10px;
  border: 1px solid var(--insight-border-strong);
  border-radius: 4px;
  color: var(--insight-ink);
  background: var(--insight-surface);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.select-control svg {
  position: absolute;
  right: 7px;
  width: 16px;
  height: 16px;
  pointer-events: none;
  color: var(--insight-muted);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.timeline {
  position: relative;
  padding: 4px 0;
}

.timeline-group__heading {
  margin: 0;
  padding: 12px 18px 7px 112px;
  color: var(--insight-ink);
  font-size: 11px;
  font-weight: 680;
  letter-spacing: 0;
}

.timeline::before {
  position: absolute;
  top: 22px;
  bottom: 22px;
  left: 22px;
  width: 1px;
  background: var(--insight-border);
  content: '';
}

.timeline-item {
  position: relative;
  display: grid;
  min-height: 96px;
  align-items: flex-start;
  grid-template-columns: 18px 72px minmax(0, 1fr);
  gap: 12px;
  padding: 11px 18px 15px;
}

.timeline-item__marker {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 6px;
  border: 2px solid var(--insight-surface);
  border-radius: 50%;
  outline: 1px solid var(--insight-primary);
  background: var(--insight-primary);
}

.timeline-item__marker--warning {
  outline-color: var(--insight-amber);
  background: var(--insight-amber);
}

.timeline-item__marker--critical {
  outline-color: var(--insight-red);
  background: var(--insight-red);
}

.timeline-item__time {
  padding-top: 3px;
  color: var(--insight-muted);
  font-size: 10px;
  line-height: 17px;
  text-align: left;
  white-space: nowrap;
}

.timeline-item__body {
  min-width: 0;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--insight-border);
}

.timeline-item:last-of-type .timeline-item__body {
  border-bottom: 0;
}

.timeline-item__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.timeline-item__title strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 620;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repeat-count {
  display: inline-flex;
  min-height: 20px;
  align-items: center;
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--insight-muted);
  background: #f3f4f6;
  font-size: 10px;
}

.timeline-item p {
  margin: 6px 0;
  color: var(--insight-muted);
  font-size: 12px;
  line-height: 18px;
}

.timeline-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  color: var(--insight-subtle);
  font-size: 10px;
}

.timeline-more {
  display: flex;
  justify-content: center;
  padding: 14px;
  border-top: 1px solid var(--insight-border);
}

@media (max-width: 680px) {
  .timeline::before {
    left: 22px;
  }

  .timeline-group__heading {
    padding-left: 52px;
  }

  .timeline-item {
    grid-template-columns: 18px minmax(0, 1fr);
    gap: 8px;
    padding-inline: 18px;
  }

  .timeline-item__time {
    grid-column: 2;
    grid-row: 1;
    padding: 0;
    text-align: left;
  }

  .timeline-item__marker {
    grid-column: 1;
    grid-row: 1 / span 2;
  }

  .timeline-item__body {
    grid-column: 2;
  }
}
</style>
