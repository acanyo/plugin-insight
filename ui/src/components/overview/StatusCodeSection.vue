<script setup lang="ts">
import { computed } from 'vue'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import { formatCompactNumber, formatPercent } from '@/utils/format'

const props = defineProps<{ statusCodes: Record<string, number> }>()
const emit = defineEmits<{ retry: [] }>()

const distribution = computed(() => {
  const entries = Object.entries(props.statusCodes)
  const total = entries.reduce((sum, [, count]) => sum + count, 0)
  return entries
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([code, count]) => ({ code, count, percent: total > 0 ? (count * 100) / total : 0 }))
})
</script>

<template>
  <section class="insight-section">
    <SectionHeading title="状态码分布" description="当前请求统计窗口" />
    <div v-if="distribution.length" class="code-distribution">
      <div v-for="item in distribution" :key="item.code" class="code-row">
        <strong class="insight-mono">{{ item.code }}</strong>
        <div class="code-row__bar">
          <i :style="{ width: formatPercent(item.percent) }"></i>
        </div>
        <span class="insight-mono">{{ formatCompactNumber(item.count) }}</span>
        <small class="insight-mono">{{ formatPercent(item.percent) }}</small>
      </div>
    </div>
    <SectionState v-else empty empty-title="当前窗口内还没有请求" @retry="emit('retry')" />
  </section>
</template>

<style scoped>
.code-distribution {
  display: flex;
  flex-direction: column;
  padding: 9px 14px;
}

.code-row {
  display: grid;
  min-height: 39px;
  align-items: center;
  grid-template-columns: 38px minmax(60px, 1fr) 60px 48px;
  gap: 10px;
  border-bottom: 1px solid var(--insight-border);
  font-size: 11px;
}

.code-row:last-child {
  border-bottom: 0;
}

.code-row__bar {
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: #edf0f2;
}

.code-row__bar i {
  display: block;
  max-width: 100%;
  height: 100%;
  background: var(--insight-cyan);
}

.code-row span,
.code-row small {
  color: var(--insight-muted);
  text-align: right;
}
</style>
