<script setup lang="ts">
defineProps<{
  label: string
  value: string
  detail: string
  tone?: 'default' | 'green' | 'amber' | 'red' | 'cyan'
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
  min-height: 112px;
  flex-direction: column;
  justify-content: space-between;
  padding: 13px 14px;
  overflow: hidden;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
}

.metric-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  background: var(--insight-border-strong);
  content: '';
}

.metric-card--green::before {
  background: var(--insight-green);
}

.metric-card--amber::before {
  background: var(--insight-amber);
}

.metric-card--red::before {
  background: var(--insight-red);
}

.metric-card--cyan::before {
  background: var(--insight-cyan);
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
  font-size: 12px;
  line-height: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card__icon {
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  place-items: center;
  color: var(--insight-subtle);
}

.metric-card__icon :deep(svg) {
  width: 17px;
  height: 17px;
}

.metric-card__value {
  margin: 6px 0 2px;
  overflow: hidden;
  color: var(--insight-ink);
  font-size: 22px;
  font-weight: 680;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
