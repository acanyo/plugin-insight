<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { VAlert, VButton, VModal } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import PressureScoreDetails from '@/components/performance/PressureScoreDetails.vue'
import LogInspector from '@/components/diagnostics/LogInspector.vue'
import PluginResourceTrends from './PluginResourceTrends.vue'
import PluginRequestsPanel from './PluginRequestsPanel.vue'
import PluginEvidenceHistory from './PluginEvidenceHistory.vue'
import PluginBaselineComparison from './PluginBaselineComparison.vue'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import { TIME_RANGE_OPTIONS, type TimeRange } from '@/types/insight'
import type { PluginDiagnosticDetail } from '@/types/diagnostics'
import '@/styles/diagnostics.css'
const props = defineProps<{ pluginId: string; range: TimeRange }>()
const emit = defineEmits<{ close: [] }>()
const selected = ref('trends')
const selectedRange = ref<TimeRange>(props.range)
const canDiagnose = computed(() => utils.permission.has(['plugin:insight:diagnose']))
const options = computed(() => [
  { label: '资源趋势', value: 'trends' },
  { label: '慢接口', value: 'requests' },
  { label: '事件与优化', value: 'history' },
  { label: '基线对比', value: 'baseline' },
  ...(canDiagnose.value ? [{ label: '相关日志', value: 'logs' }] : []),
])
const { data, error, loading, refresh } = useDiagnosticResource<PluginDiagnosticDetail>(
  'plugin-detail',
  false,
  () => ({ pluginId: props.pluginId, range: selectedRange.value }),
)
watch(selectedRange, () => void refresh())
</script>
<template>
  <VModal
    :width="980"
    :title="`${data?.plugin.displayName || pluginId} · 插件诊断`"
    :layer-closable="false"
    @close="emit('close')"
  >
    <div class="plugin-detail">
      <div class="diagnostic-controls">
        <label
          >范围<select v-model="selectedRange" aria-label="插件诊断范围">
            <option v-for="option in TIME_RANGE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select></label
        >
        <VButton :loading="loading" @click="refresh()">刷新</VButton>
      </div>
      <VAlert v-if="error" type="error" :title="error" :closable="false" />
      <template v-if="data">
        <p>
          {{ data.plugin.name }} · {{ data.plugin.version }} ·
          {{ data.plugin.enabled ? '已启用' : '已停用' }} · {{ data.plugin.phase }}
        </p>
        <PressureScoreDetails v-if="data.pressure" :consumer="data.pressure" />
        <p>{{ data.coverage }}</p>
        <SegmentedControl v-model="selected" :options="options" control-label="插件诊断分类" />
        <PluginResourceTrends v-if="selected === 'trends'" :detail="data" />
        <PluginRequestsPanel v-else-if="selected === 'requests'" :routes="data.routes" />
        <PluginEvidenceHistory v-else-if="selected === 'history'" :detail="data" />
        <PluginBaselineComparison
          v-else-if="selected === 'baseline'"
          :plugin-id="pluginId"
          :range="selectedRange"
        />
        <LogInspector
          v-else-if="selected === 'logs' && canDiagnose"
          :initial-plugin-id="pluginId"
        />
      </template>
    </div>
  </VModal>
</template>
<style scoped>
.plugin-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
