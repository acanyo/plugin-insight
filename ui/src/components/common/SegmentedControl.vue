<script setup lang="ts">
import type { SelectOption } from '@/types/insight'

defineProps<{
  modelValue: string
  options: SelectOption<string>[]
  controlLabel: string
  compact?: boolean
}>()

defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <div
    class="segmented"
    :class="{ 'segmented--compact': compact }"
    role="group"
    :aria-label="controlLabel"
  >
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :aria-pressed="modelValue === option.value"
      class="segmented__item"
      :class="{ 'segmented__item--active': modelValue === option.value }"
      @click="$emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.segmented {
  display: inline-flex;
  min-height: 32px;
  align-items: stretch;
  padding: 2px;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: #f3f4f6;
}

.segmented__item {
  min-width: 64px;
  padding: 4px 9px;
  border: 0;
  border-radius: 4px;
  color: var(--insight-muted);
  background: transparent;
  font: inherit;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  cursor: pointer;
}

.segmented__item:hover {
  color: var(--insight-ink);
}

.segmented__item--active {
  color: var(--insight-primary);
  background: var(--insight-surface);
  box-shadow: 0 1px 2px rgb(15 23 42 / 10%);
}

.segmented--compact .segmented__item {
  min-width: 50px;
  padding-inline: 7px;
}

@media (max-width: 640px) {
  .segmented {
    width: 100%;
    overflow-x: auto;
  }

  .segmented__item {
    flex: 1 0 auto;
  }
}
</style>
