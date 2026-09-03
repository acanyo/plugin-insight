<script setup lang="ts">
import { computed } from 'vue'

import type { HealthStatus, Severity } from '@/types/insight'
import { severityLabel, statusLabel } from '@/utils/format'

type Tone = HealthStatus | Severity

const props = defineProps<{
  tone: Tone
  label?: string
}>()

const resolvedLabel = computed(() => {
  if (props.label) return props.label
  if (props.tone === 'info') return severityLabel('info')
  if (props.tone === 'warning') return severityLabel('warning')
  if (props.tone === 'critical') return severityLabel('critical')
  return statusLabel(props.tone)
})
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">
    <span class="status-badge__dot" aria-hidden="true"></span>
    {{ resolvedLabel }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  min-height: 22px;
  align-items: center;
  gap: 6px;
  padding: 2px 7px;
  border: 1px solid var(--insight-border);
  border-radius: 999px;
  color: var(--insight-muted);
  background: var(--insight-surface-muted);
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  white-space: nowrap;
}

.status-badge__dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
  background: var(--insight-subtle);
}

.status-badge--healthy {
  border-color: #b9dfcf;
  color: var(--insight-green);
  background: var(--insight-green-soft);
}

.status-badge--healthy .status-badge__dot {
  background: var(--insight-green);
}

.status-badge--warning {
  border-color: #ecd59f;
  color: var(--insight-amber);
  background: var(--insight-amber-soft);
}

.status-badge--warning .status-badge__dot {
  background: var(--insight-amber);
}

.status-badge--critical {
  border-color: #efc4c6;
  color: var(--insight-red);
  background: var(--insight-red-soft);
}

.status-badge--critical .status-badge__dot {
  background: var(--insight-red);
}

.status-badge--info {
  border-color: #b7dde3;
  color: var(--insight-cyan);
  background: var(--insight-cyan-soft);
}

.status-badge--info .status-badge__dot {
  background: var(--insight-cyan);
}
</style>
