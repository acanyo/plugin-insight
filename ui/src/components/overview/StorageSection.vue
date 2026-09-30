<script setup lang="ts">
import { VButton } from '@halo-dev/components'
import { computed } from 'vue'

import SectionHeading from '@/components/common/SectionHeading.vue'
import ResourceDetailGrid, {
  type ResourceDetail,
} from '@/components/overview/ResourceDetailGrid.vue'
import ResourceMeter from '@/components/overview/ResourceMeter.vue'
import ResourceSectionLayout from '@/components/overview/ResourceSectionLayout.vue'
import { INSIGHT_MANAGE_PERMISSION } from '@/constants/permissions'
import type { OverviewResponse } from '@/types/insight'
import { formatBytes, formatNumber, ratioToPercent } from '@/utils/format'

const props = defineProps<{
  overview: OverviewResponse
  clearingData: boolean
}>()

const emit = defineEmits<{ clearMonitoringData: [] }>()

const diskPercent = computed(() =>
  props.overview.runtime.diskTotalBytes
    ? ratioToPercent(props.overview.runtime.diskUsedBytes / props.overview.runtime.diskTotalBytes)
    : 0,
)

const storagePercent = computed(() =>
  props.overview.storage.maxSizeBytes
    ? ratioToPercent(props.overview.storage.sizeBytes / props.overview.storage.maxSizeBytes)
    : 0,
)

const details = computed<ResourceDetail[]>(() => [
  { label: 'SQLite 数据', value: formatBytes(props.overview.storage.sizeBytes) },
  { label: '容量上限', value: formatBytes(props.overview.storage.maxSizeBytes) },
  { label: '磁盘已用', value: formatBytes(props.overview.runtime.diskUsedBytes) },
  { label: '磁盘总量', value: formatBytes(props.overview.runtime.diskTotalBytes) },
  { label: '历史写入', value: props.overview.storage.writePaused ? '已暂停' : '正常' },
  {
    label: '存储失败',
    value: formatNumber(props.overview.storage.failureCount),
    danger: props.overview.storage.failureCount > 0,
  },
])
</script>

<template>
  <section class="insight-section">
    <SectionHeading title="存储与磁盘" :description="overview.storage.message">
      <template #actions>
        <VButton
          v-permission="[INSIGHT_MANAGE_PERMISSION]"
          type="danger"
          size="sm"
          :loading="clearingData"
          @click="emit('clearMonitoringData')"
        >
          清空监控历史
        </VButton>
      </template>
    </SectionHeading>
    <ResourceSectionLayout>
      <template #meters>
        <ResourceMeter label="宿主磁盘" :value="diskPercent" />
        <ResourceMeter label="监控库容量" :value="storagePercent" />
      </template>
      <ResourceDetailGrid :items="details" />
    </ResourceSectionLayout>
  </section>
</template>
