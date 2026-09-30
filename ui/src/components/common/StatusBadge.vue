<script setup lang="ts">
import { VStatusDot } from '@halo-dev/components'
import { computed } from 'vue'

import type { HealthStatus, Severity } from '@/types/insight'
import { severityLabel, statusLabel } from '@/utils/format'

type Tone = HealthStatus | Severity

const props = defineProps<{
  tone: Tone
  label?: string
  animate?: boolean
}>()

const state = computed(() => {
  if (props.tone === 'healthy') return 'success'
  if (props.tone === 'warning') return 'warning'
  if (props.tone === 'critical') return 'error'
  return 'default'
})

const resolvedLabel = computed(() => {
  if (props.label) return props.label
  if (props.tone === 'info') return severityLabel('info')
  if (props.tone === 'warning') return severityLabel('warning')
  if (props.tone === 'critical') return severityLabel('critical')
  return statusLabel(props.tone)
})
</script>

<template>
  <VStatusDot :state="state" :text="resolvedLabel" :animate="animate" />
</template>
