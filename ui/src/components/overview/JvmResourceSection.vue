<script setup lang="ts">
import { computed } from 'vue'

import SectionHeading from '@/components/common/SectionHeading.vue'
import ResourceDetailGrid, {
  type ResourceDetail,
} from '@/components/overview/ResourceDetailGrid.vue'
import ResourceMeter from '@/components/overview/ResourceMeter.vue'
import ResourceSectionLayout from '@/components/overview/ResourceSectionLayout.vue'
import type { OverviewResponse } from '@/types/insight'
import { formatBytes, formatCompactNumber, formatLatency, ratioToPercent } from '@/utils/format'

const props = defineProps<{ overview: OverviewResponse }>()

const systemMemoryPercent = computed(() =>
  props.overview.runtime.systemMemoryTotalBytes
    ? ratioToPercent(
        props.overview.runtime.systemMemoryUsedBytes /
          props.overview.runtime.systemMemoryTotalBytes,
      )
    : 0,
)

const details = computed<ResourceDetail[]>(() => [
  { label: '非堆内存', value: formatBytes(props.overview.jvm.nonHeapUsedBytes) },
  {
    label: '活动 / 峰值线程',
    value: `${props.overview.jvm.liveThreads} / ${props.overview.jvm.peakThreads}`,
  },
  { label: '守护线程', value: String(props.overview.jvm.daemonThreads) },
  {
    label: '死锁线程',
    value: String(props.overview.jvm.deadlockedThreads),
    danger: props.overview.jvm.deadlockedThreads > 0,
  },
  { label: 'GC 次数', value: formatCompactNumber(props.overview.jvm.gcCount) },
  { label: 'GC 停顿', value: formatLatency(props.overview.jvm.gcPauseMs) },
])
</script>

<template>
  <section class="insight-section">
    <SectionHeading
      title="JVM 与宿主资源"
      description="内存、线程与垃圾回收；Linux 系统内存按 MemAvailable 口径计算"
    />
    <ResourceSectionLayout>
      <template #meters>
        <ResourceMeter label="JVM 堆" :value="overview.jvm.heapUsedPercent" />
        <ResourceMeter
          label="系统内存"
          :value="systemMemoryPercent"
          :detail="`${formatBytes(overview.runtime.systemMemoryUsedBytes)} / ${formatBytes(overview.runtime.systemMemoryTotalBytes)} · 可用内存口径`"
        />
      </template>
      <ResourceDetailGrid :items="details" />
    </ResourceSectionLayout>
  </section>
</template>
