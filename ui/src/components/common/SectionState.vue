<script setup lang="ts">
import { VAlert, VButton, VEmpty, VLoading } from '@halo-dev/components'

defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  emptyTitle?: string
  emptyDescription?: string
}>()

defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="section-state" role="status" aria-live="polite">
    <VLoading />
    <span>正在读取监控数据</span>
  </div>
  <div v-else-if="error" class="section-state section-state--error" role="alert">
    <VAlert type="error" :title="error" :closable="false" />
    <VButton size="sm" @click="$emit('retry')">重试</VButton>
  </div>
  <VEmpty v-else-if="empty" :title="emptyTitle || '暂无数据'">
    <template v-if="emptyDescription" #message>
      {{ emptyDescription }}
    </template>
    <template #actions>
      <VButton size="sm" @click="$emit('retry')">刷新</VButton>
    </template>
  </VEmpty>
  <slot v-else />
</template>

<style scoped>
.section-state {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  color: var(--insight-muted);
  font-size: 13px;
}

.section-state--error {
  flex-direction: column;
  align-items: stretch;
}
</style>
