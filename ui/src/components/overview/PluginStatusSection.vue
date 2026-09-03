<script setup lang="ts">
import { computed } from 'vue'

import SectionHeading from '@/components/common/SectionHeading.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import type { AlertIncident, HealthStatus, PluginRuntimeInfo } from '@/types/insight'
import { formatDateTime, severityTone } from '@/utils/format'

const props = defineProps<{
  plugins: PluginRuntimeInfo[]
  activeAlerts: AlertIncident[]
}>()

const summary = computed(() => {
  const enabled = props.plugins.filter((plugin) => plugin.enabled).length
  const abnormal = props.plugins.filter((plugin) => {
    if (!plugin.enabled) return false
    return !['STARTED', 'READY', 'RESOLVED'].includes(plugin.phase.toUpperCase())
  }).length
  return { total: props.plugins.length, enabled, abnormal }
})

const sortedPlugins = computed(() =>
  [...props.plugins].sort((left, right) => {
    const leftAbnormal =
      pluginTone(left) === 'critical' ? 0 : pluginTone(left) === 'warning' ? 1 : 2
    const rightAbnormal =
      pluginTone(right) === 'critical' ? 0 : pluginTone(right) === 'warning' ? 1 : 2
    return (
      leftAbnormal - rightAbnormal ||
      (left.displayName || left.name).localeCompare(right.displayName || right.name, 'zh-CN')
    )
  }),
)

function pluginTone(plugin: PluginRuntimeInfo): HealthStatus {
  if (!plugin.enabled) return 'unknown'
  const phase = plugin.phase.toUpperCase()
  if (phase.includes('FAIL') || phase.includes('ERROR')) return 'critical'
  if (phase === 'STARTED' || phase === 'READY') return 'healthy'
  return 'warning'
}

function phaseLabel(plugin: PluginRuntimeInfo): string {
  if (!plugin.enabled) return '已停用'
  return plugin.phase || '未知阶段'
}
</script>

<template>
  <section class="insight-section plugin-summary-section">
    <SectionHeading title="Halo 插件状态" description="已安装插件的启用与实际运行阶段" />
    <dl class="summary-details">
      <div>
        <dt>已安装</dt>
        <dd class="insight-mono">{{ summary.total }}</dd>
      </div>
      <div>
        <dt>已启用</dt>
        <dd class="insight-mono">{{ summary.enabled }}</dd>
      </div>
      <div>
        <dt>状态异常</dt>
        <dd class="insight-mono" :class="{ danger: summary.abnormal > 0 }">
          {{ summary.abnormal }}
        </dd>
      </div>
    </dl>
    <div v-if="activeAlerts.length" class="active-alert-preview">
      <div v-for="alert in activeAlerts.slice(0, 3)" :key="alert.id">
        <StatusBadge :tone="severityTone(alert.severity)" />
        <span>{{ alert.title }}</span>
      </div>
    </div>
    <details v-if="plugins.length" class="plugin-details">
      <summary>查看 {{ plugins.length }} 个插件明细</summary>
      <div class="plugin-details__list">
        <div v-for="plugin in sortedPlugins" :key="plugin.name" class="plugin-details__row">
          <div class="plugin-details__identity">
            <strong>{{ plugin.displayName || plugin.name }}</strong>
            <span class="insight-mono">{{ plugin.name }} · {{ plugin.version || '未知版本' }}</span>
          </div>
          <div class="plugin-details__state">
            <StatusBadge :tone="pluginTone(plugin)" :label="phaseLabel(plugin)" />
            <time v-if="plugin.lastStartTime" :datetime="plugin.lastStartTime">
              启动于 {{ formatDateTime(plugin.lastStartTime) }}
            </time>
          </div>
        </div>
      </div>
    </details>
  </section>
</template>

<style scoped>
.summary-details {
  display: grid;
  margin: 0;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-details div {
  padding: 14px;
  border-right: 1px solid var(--insight-border);
  text-align: center;
}

.summary-details div:last-child {
  border-right: 0;
}

.summary-details dt {
  color: var(--insight-muted);
  font-size: 11px;
}

.summary-details dd {
  margin: 5px 0 0;
  font-size: 20px;
  font-weight: 680;
}

.active-alert-preview {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--insight-border);
}

.active-alert-preview > div {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border-bottom: 1px solid var(--insight-border);
}

.active-alert-preview > div:last-child {
  border-bottom: 0;
}

.active-alert-preview span:last-child {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.danger {
  color: var(--insight-red);
}

.plugin-details {
  border-top: 1px solid var(--insight-border);
}

.plugin-details summary {
  padding: 11px 14px;
  color: var(--insight-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
}

.plugin-details[open] summary {
  border-bottom: 1px solid var(--insight-border);
}

.plugin-details__list {
  max-height: 310px;
  overflow-y: auto;
}

.plugin-details__row {
  display: flex;
  min-width: 0;
  min-height: 56px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--insight-border);
}

.plugin-details__row:last-child {
  border-bottom: 0;
}

.plugin-details__identity,
.plugin-details__state {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.plugin-details__identity strong,
.plugin-details__identity span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-details__identity strong {
  font-size: 12px;
}

.plugin-details__identity span,
.plugin-details__state time {
  color: var(--insight-subtle);
  font-size: 9px;
}

.plugin-details__state {
  align-items: flex-end;
  gap: 3px;
  text-align: right;
}

@media (max-width: 620px) {
  .plugin-details__row {
    align-items: flex-start;
    flex-direction: column;
  }

  .plugin-details__state {
    align-items: flex-start;
    text-align: left;
  }
}
</style>
