<script setup lang="ts">
import { VAlert, VButton, VLoading, VModal } from '@halo-dev/components'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiShieldCheckLine from '~icons/ri/shield-check-line'

import InsightOverheadControl from '@/components/optimization/InsightOverheadControl.vue'
import PluginOptimizationCard from '@/components/optimization/PluginOptimizationCard.vue'
import RuntimeDiagnosisSummary from '@/components/optimization/RuntimeDiagnosisSummary.vue'
import type { OptimizationStatus, PluginConsumerItem } from '@/types/insight'

defineProps<{
  status?: OptimizationStatus
  loading: boolean
  error?: string
  applying: boolean
  rollingBack: boolean
  disablingPluginId?: string
}>()

const emit = defineEmits<{
  close: []
  apply: []
  rollback: []
  retry: []
  disablePlugin: [consumer: PluginConsumerItem]
}>()
</script>

<template>
  <VModal :width="780" title="性能诊断与优化" :layer-closable="false" @close="emit('close')">
    <div class="optimization-modal">
      <VLoading v-if="loading && !status" />
      <VAlert v-else-if="error && !status" type="error" :title="error" :closable="false">
        <template #actions>
          <VButton size="sm" @click="emit('retry')">重新诊断</VButton>
        </template>
      </VAlert>

      <template v-else-if="status">
        <RuntimeDiagnosisSummary :diagnosis="status.runtime" />

        <PluginOptimizationCard
          :consumer="status.topConsumer"
          :resource-sampling-enabled="status.pluginResourceSamplingEnabled"
          :disabling="disablingPluginId === status.topConsumer?.pluginId"
          @disable="emit('disablePlugin', $event)"
        />

        <div class="safety-note">
          <RiShieldCheckLine aria-hidden="true" />
          <p>
            插件共享 Halo 的 JVM 堆，无法精确在线释放某个插件的驻留内存。Insight 不会强制
            GC；停用插件必须由你确认，并建议观察至少 5 分钟趋势。
          </p>
        </div>

        <InsightOverheadControl
          :status="status"
          :applying="applying"
          :rolling-back="rollingBack"
          @apply="emit('apply')"
          @rollback="emit('rollback')"
        />
      </template>
    </div>

    <template #footer>
      <div class="optimization-actions">
        <VButton :loading="loading" @click="emit('retry')">
          <template #icon><RiRefreshLine /></template>
          重新诊断
        </VButton>
        <VButton type="primary" @click="emit('close')">完成</VButton>
      </div>
    </template>
  </VModal>
</template>

<style scoped>
.optimization-modal {
  display: flex;
  min-height: 280px;
  max-height: min(72vh, 760px);
  overflow-y: auto;
  flex-direction: column;
  gap: 12px;
  padding-right: 2px;
}

.safety-note {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 9px 12px;
  border-left: 2px solid var(--insight-primary);
  color: var(--insight-muted);
  background: var(--insight-primary-soft);
}

.safety-note svg {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--insight-primary);
}

.safety-note p {
  margin: 0;
  font-size: 11px;
  line-height: 1.55;
}

.optimization-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 640px) {
  .optimization-modal {
    max-height: 74vh;
  }
}
</style>
