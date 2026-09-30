<script setup lang="ts">
import { computed } from 'vue'

import { formatPercent } from '@/utils/format'

const props = defineProps<{
  label: string
  value: number
  detail?: string
}>()

const normalizedValue = computed(() => Math.max(0, Math.min(100, props.value)))
const tone = computed(() => {
  if (normalizedValue.value >= 90) return 'critical'
  if (normalizedValue.value >= 75) return 'warning'
  return 'healthy'
})
</script>

<template>
  <div class="resource-meter">
    <div class="resource-meter__header">
      <span>{{ label }}</span>
      <strong class="insight-mono">{{ formatPercent(normalizedValue) }}</strong>
    </div>
    <div class="resource-meter__track">
      <i
        :class="`resource-meter__fill--${tone}`"
        :style="{ width: formatPercent(normalizedValue) }"
      ></i>
    </div>
    <small v-if="detail">{{ detail }}</small>
  </div>
</template>

<style scoped>
.resource-meter {
  margin-bottom: 13px;
}

.resource-meter__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
  color: var(--insight-muted);
  font-size: 12px;
}

.resource-meter__header strong {
  color: var(--insight-ink);
}

.resource-meter__track {
  width: 100%;
  height: 7px;
  overflow: hidden;
  border-radius: 3px;
  background: #e5e7eb;
}

.resource-meter__track i {
  display: block;
  height: 100%;
}

.resource-meter small {
  display: block;
  margin-top: 5px;
  color: var(--insight-muted);
  font-size: 11px;
  line-height: 16px;
}

.resource-meter__fill--healthy {
  background: var(--insight-green);
}

.resource-meter__fill--warning {
  background: var(--insight-amber);
}

.resource-meter__fill--critical {
  background: var(--insight-red);
}
</style>
