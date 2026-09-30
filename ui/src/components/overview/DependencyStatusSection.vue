<script setup lang="ts">
import { type Component } from 'vue'
import RiDatabase2Line from '~icons/ri/database-2-line'
import RiDeleteBin6Line from '~icons/ri/delete-bin-6-line'
import RiFolderImageLine from '~icons/ri/folder-image-line'
import RiLinksLine from '~icons/ri/links-line'
import RiPulseLine from '~icons/ri/pulse-line'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiSearchEyeLine from '~icons/ri/search-eye-line'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import type { DependencyStatus } from '@/types/insight'
import { healthStatus } from '@/utils/format'

defineProps<{ dependencies: DependencyStatus[] }>()
const emit = defineEmits<{ retry: [] }>()

const dependencyIcons: Record<string, Component> = {
  database: RiDatabase2Line,
  search: RiSearchEyeLine,
  attachments: RiFolderImageLine,
  collector: RiPulseLine,
  'halo-runtime-refresh': RiRefreshLine,
  'history-retention': RiDeleteBin6Line,
}

function dependencyIcon(key: string): Component {
  return dependencyIcons[key] || RiLinksLine
}

function providerLabel(displayName: string | null | undefined, pluginId: string): string {
  return displayName && displayName !== pluginId ? `${displayName}（${pluginId}）` : pluginId
}
</script>

<template>
  <section class="insight-section">
    <SectionHeading title="Halo 依赖状态" description="数据库、搜索与附件存储" />
    <div v-if="dependencies.length" class="service-list">
      <div v-for="service in dependencies" :key="service.key" class="service-row">
        <span
          class="service-row__icon"
          :class="`service-row__icon--${healthStatus(service.status)}`"
          aria-hidden="true"
        >
          <component :is="dependencyIcon(service.key)" />
        </span>
        <div class="service-row__copy">
          <strong>{{ service.name }}</strong>
          <span>{{ service.message }}</span>
          <small v-if="service.providers?.length">
            关联插件：{{
              service.providers
                .map((provider) => providerLabel(provider.displayName, provider.pluginId))
                .join('、')
            }}
          </small>
        </div>
        <StatusBadge :tone="healthStatus(service.status)" />
      </div>
    </div>
    <SectionState v-else empty empty-title="暂无依赖检查结果" @retry="emit('retry')" />
  </section>
</template>

<style scoped>
.service-list {
  display: flex;
  flex-direction: column;
}

.service-row {
  display: grid;
  min-height: 61px;
  align-items: center;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--insight-border);
}

.service-row__icon {
  display: grid;
  width: 28px;
  height: 28px;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  color: var(--insight-muted);
  background: var(--insight-surface-muted);
  place-items: center;
}

.service-row__icon svg {
  width: 14px;
  height: 14px;
}

.service-row__icon--healthy {
  border-color: #bbf7d0;
  color: var(--insight-green);
  background: var(--insight-green-soft);
}

.service-row__icon--warning {
  border-color: #fde68a;
  color: var(--insight-amber);
  background: var(--insight-amber-soft);
}

.service-row__icon--critical {
  border-color: #fecaca;
  color: var(--insight-red);
  background: var(--insight-red-soft);
}

.service-row:last-child {
  border-bottom: 0;
}

.service-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.service-row__copy strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 620;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-row__copy span {
  color: var(--insight-muted);
  font-size: 11px;
  line-height: 16px;
}

.service-row__copy small {
  color: var(--insight-subtle);
  font-size: 10px;
  line-height: 15px;
}
</style>
