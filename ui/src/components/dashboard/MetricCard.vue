<script setup lang="ts">
defineProps<{
  label: string
  value: string
  detail: string
  tone?: 'default' | 'primary' | 'healthy' | 'warning' | 'critical'
}>()
</script>

<template>
  <article class="metric-card" :class="`metric-card--${tone || 'default'}`">
    <div class="metric-card__topline">
      <span class="metric-card__label">{{ label }}</span>
      <span v-if="$slots.icon" class="metric-card__icon"><slot name="icon" /></span>
    </div>
    <strong class="metric-card__value insight-mono">{{ value }}</strong>
    <span class="metric-card__detail">{{ detail }}</span>
  </article>
</template>

<style scoped>
.metric-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 108px;
  flex-direction: column;
  justify-content: space-between;
  padding: 15px 16px 14px;
  overflow: hidden;
  border: 0;
  border-right: 1px solid var(--insight-border);
  border-radius: 0;
  background: var(--insight-surface);
}

.metric-card__topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric-card__label,
.metric-card__detail {
  overflow: hidden;
  color: var(--insight-muted);
  font-size: var(--insight-text-sm);
  line-height: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card__icon {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 6px;
  place-items: center;
  color: var(--insight-subtle);
  background: var(--insight-surface-muted);
}

.metric-card__icon :deep(svg) {
  width: 15px;
  height: 15px;
}

.metric-card--warning .metric-card__icon {
  color: var(--insight-amber);
  background: var(--insight-amber-soft);
}

.metric-card--critical .metric-card__icon {
  color: var(--insight-red);
  background: var(--insight-red-soft);
}

.metric-card__value {
  margin: 5px 0 2px;
  overflow: hidden;
  color: var(--insight-ink);
  font-size: 22px;
  font-weight: 680;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
