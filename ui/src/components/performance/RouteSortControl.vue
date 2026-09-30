<script setup lang="ts">
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import { ROUTE_SORT_OPTIONS } from '@/constants/routeRanking'
import type { RequestRouteSort } from '@/types/insight'

defineProps<{
  modelValue: RequestRouteSort
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RequestRouteSort]
}>()
</script>

<template>
  <SegmentedControl
    :model-value="modelValue"
    :options="ROUTE_SORT_OPTIONS"
    control-label="接口排行排序"
    compact
    @update:model-value="emit('update:modelValue', $event as RequestRouteSort)"
  />
</template>
