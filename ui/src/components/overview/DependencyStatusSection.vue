<script setup lang="ts">
import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import type { DependencyStatus } from '@/types/insight'
import { healthStatus } from '@/utils/format'

defineProps<{ dependencies: DependencyStatus[] }>()
const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="insight-section">
    <SectionHeading title="Halo 依赖状态" description="数据库、搜索与附件存储" />
    <div v-if="dependencies.length" class="service-list">
      <div v-for="service in dependencies" :key="service.key" class="service-row">
        <div class="service-row__copy">
          <strong>{{ service.name }}</strong>
          <span>{{ service.message }}</span>
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
  display: flex;
  min-height: 61px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--insight-border);
}

.service-row:last-child {
  border-bottom: 0;
}

.service-row__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.service-row__copy strong,
.service-row__copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-row__copy strong {
  font-size: 13px;
  font-weight: 620;
  line-height: 18px;
}

.service-row__copy span {
  color: var(--insight-muted);
  font-size: 11px;
  line-height: 16px;
}
</style>
