<script setup lang="ts">
import { computed, ref } from 'vue'
import { utils } from '@halo-dev/ui-shared'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import MemoryDiagnostics from '@/components/diagnostics/MemoryDiagnostics.vue'
import InsightOverheadPanel from '@/components/diagnostics/InsightOverheadPanel.vue'
import ObservationHistory from '@/components/diagnostics/ObservationHistory.vue'
import BusinessTasks from '@/components/diagnostics/BusinessTasks.vue'
import DeliveryHistory from '@/components/diagnostics/DeliveryHistory.vue'
import ThreadInspector from '@/components/diagnostics/ThreadInspector.vue'
import LogInspector from '@/components/diagnostics/LogInspector.vue'
import JfrInspector from '@/components/diagnostics/JfrInspector.vue'
import ExternalMonitorHelp from '@/components/diagnostics/ExternalMonitorHelp.vue'
import '@/styles/diagnostics.css'
const selected = ref('memory')
const canManage = computed(() => utils.permission.has(['plugin:insight:manage']))
const canDiagnose = computed(() => utils.permission.has(['plugin:insight:diagnose']))
const options = computed(() => [
  { label: '内存与 GC', value: 'memory' },
  { label: '自身开销', value: 'overhead' },
  { label: '优化效果', value: 'observations' },
  { label: '业务任务', value: 'tasks' },
  ...(canManage.value ? [{ label: '通知投递', value: 'deliveries' }] : []),
  ...(canDiagnose.value
    ? [
        { label: '线程栈', value: 'threads' },
        { label: '日志', value: 'logs' },
        { label: 'JFR', value: 'jfr' },
      ]
    : []),
  { label: '站外监测', value: 'external' },
])
</script>
<template>
  <div class="diagnostics-panel">
    <SegmentedControl v-model="selected" :options="options" control-label="诊断分类" />
    <MemoryDiagnostics v-if="selected === 'memory'" />
    <InsightOverheadPanel v-else-if="selected === 'overhead'" />
    <ObservationHistory v-else-if="selected === 'observations'" />
    <BusinessTasks v-else-if="selected === 'tasks'" />
    <DeliveryHistory v-else-if="selected === 'deliveries' && canManage" />
    <ThreadInspector v-else-if="selected === 'threads' && canDiagnose" />
    <LogInspector v-else-if="selected === 'logs' && canDiagnose" />
    <JfrInspector v-else-if="selected === 'jfr' && canDiagnose" />
    <ExternalMonitorHelp v-else-if="selected === 'external'" />
  </div>
</template>
