<script setup lang="ts">
import { VButton } from '@halo-dev/components'
import RiLeafLine from '~icons/ri/leaf-line'
import RiRestartLine from '~icons/ri/restart-line'

import StatusBadge from '@/components/common/StatusBadge.vue'
import { INSIGHT_MANAGE_PERMISSION } from '@/constants/permissions'
import type { OptimizationStatus } from '@/types/insight'

defineProps<{
  status: OptimizationStatus
  applying: boolean
  rollingBack: boolean
}>()

const emit = defineEmits<{
  apply: []
  rollback: []
}>()
</script>

<template>
  <section class="overhead-control">
    <div class="overhead-copy">
      <div>
        <StatusBadge
          :tone="status.enabled ? 'healthy' : 'unknown'"
          :label="status.enabled ? '已降低' : '标准采集'"
        />
        <strong>Insight 自身开销</strong>
      </div>
      <p>仅调整 Insight 的采样与线程扫描，并维护监控数据库；不会优化 Halo JVM 或修改其他插件。</p>
    </div>

    <div class="overhead-values">
      <span>采样 {{ status.effectiveSamplingSeconds }}s</span>
      <span>扫描上限 {{ status.effectivePluginThreadLimit }}</span>
    </div>

    <VButton
      v-if="status.enabled"
      v-permission="[INSIGHT_MANAGE_PERMISSION]"
      size="sm"
      :loading="rollingBack"
      :disabled="applying"
      @click="emit('rollback')"
    >
      <template #icon><RiRestartLine /></template>
      恢复标准采集
    </VButton>
    <VButton
      v-else
      v-permission="[INSIGHT_MANAGE_PERMISSION]"
      size="sm"
      :loading="applying"
      :disabled="!status.canApply || rollingBack"
      @click="emit('apply')"
    >
      <template #icon><RiLeafLine /></template>
      降低监控器开销
    </VButton>
  </section>
</template>

<style scoped>
.overhead-control {
  display: grid;
  align-items: center;
  grid-template-columns: minmax(240px, 1fr) auto auto;
  gap: 16px;
  padding: 11px 13px;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface-muted);
}

.overhead-copy > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.overhead-copy strong {
  color: var(--insight-ink);
  font-size: 12px;
  font-weight: 650;
}

.overhead-copy p {
  margin: 5px 0 0;
  color: var(--insight-muted);
  font-size: 11px;
  line-height: 1.5;
}

.overhead-values {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 3px;
}

.overhead-values span {
  color: var(--insight-subtle);
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .overhead-control {
    align-items: flex-start;
    grid-template-columns: 1fr;
  }

  .overhead-values {
    align-items: flex-start;
  }
}
</style>
