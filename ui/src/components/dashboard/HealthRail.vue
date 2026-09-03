<script setup lang="ts">
import { computed, type Component } from 'vue'
import RiAlarmWarningLine from '~icons/ri/alarm-warning-line'
import RiDatabase2Line from '~icons/ri/database-2-line'
import RiPulseLine from '~icons/ri/pulse-line'
import RiServerLine from '~icons/ri/server-line'

import StatusBadge from '@/components/common/StatusBadge.vue'
import type { HealthRailItem } from '@/types/insight'

const props = defineProps<{
  items: HealthRailItem[]
}>()

const iconMap: Record<HealthRailItem['key'], Component> = {
  runtime: RiServerLine,
  storage: RiDatabase2Line,
  requests: RiPulseLine,
  alerts: RiAlarmWarningLine,
}

const resolvedItems = computed(() =>
  props.items.map((item) => ({ ...item, icon: iconMap[item.key] })),
)
</script>

<template>
  <section class="health-rail" aria-label="系统健康状态">
    <article
      v-for="item in resolvedItems"
      :key="item.key"
      class="health-rail__item"
      :class="`health-rail__item--${item.status}`"
    >
      <component :is="item.icon" class="health-rail__icon" aria-hidden="true" />
      <div class="health-rail__copy">
        <div class="health-rail__label-row">
          <span>{{ item.label }}</span>
          <StatusBadge :tone="item.status" />
        </div>
        <strong class="insight-mono">{{ item.value }}</strong>
        <small>{{ item.detail }}</small>
      </div>
    </article>
  </section>
</template>

<style scoped>
.health-rail {
  display: grid;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.health-rail__item {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 104px;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  padding: 13px 14px;
  border-right: 1px solid var(--insight-border);
}

.health-rail__item:last-child {
  border-right: 0;
}

.health-rail__item::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: var(--insight-subtle);
  content: '';
}

.health-rail__item--healthy::before {
  background: var(--insight-green);
}

.health-rail__item--warning::before {
  background: var(--insight-amber);
}

.health-rail__item--critical::before {
  background: var(--insight-red);
}

.health-rail__icon {
  width: 20px;
  height: 20px;
  margin-top: 1px;
  color: var(--insight-muted);
}

.health-rail__copy {
  min-width: 0;
}

.health-rail__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--insight-muted);
  font-size: 12px;
}

.health-rail strong {
  display: block;
  margin-top: 7px;
  overflow: hidden;
  font-size: 18px;
  font-weight: 680;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.health-rail small {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: var(--insight-muted);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .health-rail {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .health-rail__item:nth-child(2) {
    border-right: 0;
  }

  .health-rail__item:nth-child(-n + 2) {
    border-bottom: 1px solid var(--insight-border);
  }
}

@media (max-width: 560px) {
  .health-rail {
    grid-template-columns: 1fr;
  }

  .health-rail__item,
  .health-rail__item:nth-child(2) {
    min-height: 92px;
    border-right: 0;
    border-bottom: 1px solid var(--insight-border);
  }

  .health-rail__item:last-child {
    border-bottom: 0;
  }
}
</style>
