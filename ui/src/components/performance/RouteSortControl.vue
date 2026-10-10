<script setup lang="ts">
import { ROUTE_SORT_OPTIONS } from '@/constants/routeRanking'
import type { RequestRouteSort } from '@/types/insight'

defineProps<{
  modelValue: RequestRouteSort
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RequestRouteSort]
}>()

function updateSort(event: Event): void {
  const target = event.target
  if (target instanceof HTMLSelectElement) {
    emit('update:modelValue', target.value as RequestRouteSort)
  }
}
</script>

<template>
  <label class="route-sort-select">
    <span>排序</span>
    <select :value="modelValue" aria-label="接口排行排序" @change="updateSort">
      <option v-for="option in ROUTE_SORT_OPTIONS" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </label>
</template>

<style scoped>
.route-sort-select {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--insight-subtle);
  font-size: 11px;
  font-weight: 600;
}

.route-sort-select select {
  min-width: 104px;
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

.route-sort-select select:hover,
.route-sort-select select:focus {
  border-color: var(--insight-primary);
  box-shadow: 0 0 0 3px var(--insight-primary-soft);
  outline: 0;
}
</style>
