<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import RiAlarmWarningLine from '~icons/ri/alarm-warning-line'
import RiDatabase2Line from '~icons/ri/database-2-line'
import RiRouteLine from '~icons/ri/route-line'
import RiServerLine from '~icons/ri/server-line'

import StatusBadge from '@/components/common/StatusBadge.vue'
import type { HealthRailItem } from '@/types/insight'

const props = defineProps<{
  items: HealthRailItem[]
}>()

const openKey = ref<HealthRailItem['key'] | null>(null)

function toggleExplanation(key: HealthRailItem['key']) {
  openKey.value = openKey.value === key ? null : key
}

const iconMap: Record<HealthRailItem['key'], Component> = {
  runtime: RiServerLine,
  storage: RiDatabase2Line,
  requests: RiRouteLine,
  alerts: RiAlarmWarningLine,
}

const resolvedItems = computed(() =>
  props.items.map((item) => ({ ...item, icon: iconMap[item.key] })),
)
</script>

<template>
  <section class="health-rail" aria-label="系统健康状态">
    <article v-for="item in resolvedItems" :key="item.key" class="health-rail__item">
      <component :is="item.icon" class="health-rail__icon" aria-hidden="true" />
      <div class="health-rail__copy">
        <div class="health-rail__label-row">
          <span class="health-rail__label">{{ item.label }}</span>
          <span class="health-rail__status-help" :class="{ 'is-open': openKey === item.key }">
            <button
              type="button"
              class="health-rail__status-trigger"
              :aria-label="`${item.label}状态，查看原因`"
              :aria-expanded="openKey === item.key"
              @click.stop="toggleExplanation(item.key)"
            >
              <StatusBadge :tone="item.status" />
            </button>
            <span v-if="item.explanation" class="health-rail__tooltip" role="tooltip">
              {{ item.explanation }}
            </span>
          </span>
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
  overflow: visible;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.health-rail__item {
  display: grid;
  min-width: 0;
  min-height: 116px;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  padding: 17px 18px;
  border-right: 1px solid var(--insight-border);
}

.health-rail__item:last-child {
  border-right: 0;
}

.health-rail__icon {
  width: 19px;
  height: 19px;
  margin-top: 1px;
  color: var(--insight-subtle);
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
  font-size: var(--insight-text-sm);
  font-weight: 600;
  line-height: 20px;
}

.health-rail__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.health-rail__status-help {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.health-rail__status-trigger {
  display: inline-flex;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
}

.health-rail__status-trigger:focus-visible {
  outline: 2px solid var(--insight-accent);
  outline-offset: 2px;
}

.health-rail__tooltip {
  position: absolute;
  z-index: 5;
  top: calc(100% + 8px);
  right: 0;
  width: max-content;
  max-width: 280px;
  padding: 8px 10px;
  border: 1px solid var(--insight-border);
  border-radius: 5px;
  background: var(--insight-surface);
  box-shadow: 0 6px 18px rgb(15 23 42 / 14%);
  color: var(--insight-ink);
  font-size: var(--insight-text-xs);
  font-weight: 500;
  line-height: 18px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-2px);
  transition:
    opacity 120ms ease,
    transform 120ms ease;
  white-space: normal;
}

.health-rail__status-help:hover .health-rail__tooltip,
.health-rail__status-help:focus-within .health-rail__tooltip,
.health-rail__status-help.is-open .health-rail__tooltip {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.health-rail strong {
  display: block;
  margin-top: 8px;
  overflow: hidden;
  font-size: 19px;
  font-weight: 680;
  line-height: 26px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.health-rail small {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: var(--insight-muted);
  font-size: var(--insight-text-xs);
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .health-rail {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .health-rail__item:nth-child(-n + 2) {
    border-bottom: 1px solid var(--insight-border);
  }

  .health-rail__item:nth-child(2n) {
    border-right: 0;
  }
}

@media (max-width: 560px) {
  .health-rail {
    grid-template-columns: 1fr;
  }

  .health-rail__item,
  .health-rail__item:nth-child(2) {
    min-height: 104px;
    border-right: 0;
    border-bottom: 1px solid var(--insight-border);
  }

  .health-rail__item:last-child {
    border-bottom: 0;
  }
}
</style>
