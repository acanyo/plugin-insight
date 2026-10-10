<script setup lang="ts">
import { computed } from 'vue'

import SectionHeading from '@/components/common/SectionHeading.vue'
import SectionState from '@/components/common/SectionState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import PressureScoreDetails from './PressureScoreDetails.vue'
import type { ConsumerPressureLevel, HealthStatus, PluginConsumerItem } from '@/types/insight'
import { formatBytes, formatPercent } from '@/utils/format'

const props = defineProps<{
  consumers?: PluginConsumerItem[]
  loading: boolean
  error?: string
}>()

const emit = defineEmits<{ retry: []; selectPlugin: [pluginId: string] }>()

const visibleConsumers = computed(() => props.consumers?.slice(0, 5) ?? [])

const pressureLabels: Record<ConsumerPressureLevel, string> = {
  high: '高压力',
  medium: '中等压力',
  low: '低压力',
  none: '无明显压力',
}

const resourceLabels: Record<PluginConsumerItem['dominantResource'], string> = {
  cpu: 'CPU',
  'memory-allocation': '内存分配',
  'request-time': '请求耗时',
  threads: '线程',
}

function consumerTone(level: ConsumerPressureLevel): HealthStatus {
  if (level === 'high') return 'critical'
  if (level === 'medium') return 'warning'
  if (level === 'low') return 'healthy'
  return 'unknown'
}
</script>

<template>
  <section class="insight-section">
    <SectionHeading
      title="持续高消耗插件"
      description="按分钟判断是否反复位于 CPU、内存分配或请求耗时前列，单次峰值不会直接判定为持续问题"
    />
    <SectionState
      :loading="loading && !consumers"
      :error="!consumers ? error : undefined"
      :empty="!loading && !error && !visibleConsumers.length"
      empty-title="暂无持续消耗样本"
      empty-description="至少积累 3 分钟插件活动后，才能判断是否存在持续高消耗。"
      @retry="emit('retry')"
    >
      <div v-if="visibleConsumers.length" class="consumer-list">
        <div
          v-for="(consumer, index) in visibleConsumers"
          :key="consumer.pluginId"
          class="consumer-row"
        >
          <span class="consumer-rank insight-mono">{{ index + 1 }}</span>
          <div class="consumer-identity">
            <button class="consumer-link" @click="emit('selectPlugin', consumer.pluginId)">
              {{ consumer.displayName || consumer.pluginId }}
            </button>
            <span class="insight-mono">{{ consumer.pluginId }}</span>
          </div>
          <div class="consumer-signal">
            <StatusBadge
              :tone="consumerTone(consumer.pressureLevel)"
              :label="
                consumer.sustained
                  ? pressureLabels[consumer.pressureLevel]
                  : consumer.confidence === 'insufficient'
                    ? '样本不足'
                    : '暂未持续'
              "
            />
            <span>主要消耗：{{ resourceLabels[consumer.dominantResource] }}</span>
          </div>
          <div class="consumer-metric">
            <strong class="insight-mono">{{ consumer.pressureScore.toFixed(0) }}</strong>
            <span>压力评分</span>
          </div>
          <div class="consumer-metric">
            <strong class="insight-mono">
              {{ consumer.topConsumerMinutes }} / {{ consumer.observedMinutes }} 分钟
            </strong>
            <span>位于首位 / 已观察</span>
          </div>
          <div class="consumer-metric">
            <strong class="insight-mono">{{ formatPercent(consumer.persistencePercent) }}</strong>
            <span>首位持续率</span>
          </div>
          <div class="consumer-metric consumer-metric--wide">
            <strong class="insight-mono"
              >{{ formatBytes(consumer.allocatedBytesPerMinute) }}/min</strong
            >
            <span>
              CPU 份额 {{ formatPercent(consumer.cpuSharePercent) }} · 请求份额
              {{ formatPercent(consumer.requestLoadSharePercent) }}
            </span>
          </div>
          <PressureScoreDetails class="consumer-score-details" :consumer="consumer" />
        </div>
      </div>
    </SectionState>
    <p class="consumer-note">
      CPU 份额表示插件占采样总 CPU
      耗时的比例；内存指标表示区间分配速率，不等于插件当前驻留堆，也不能单独作为内存泄漏结论。
    </p>
  </section>
</template>

<style scoped>
.consumer-list {
  display: flex;
  flex-direction: column;
}

.consumer-row {
  display: grid;
  min-height: 70px;
  align-items: center;
  grid-template-columns:
    32px minmax(180px, 1.3fr) minmax(150px, 1fr) repeat(3, minmax(92px, 0.7fr))
    minmax(170px, 1fr);
  gap: 14px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--insight-border);
}

.consumer-row:last-child {
  border-bottom: 0;
}

.consumer-rank {
  color: var(--insight-subtle);
  font-size: 12px;
  text-align: center;
}

.consumer-link {
  text-align: left;
  color: var(--insight-primary);
  font-size: 12px;
  font-weight: 650;
}

.consumer-score-details {
  grid-column: 2 / -1;
}

.consumer-identity,
.consumer-signal,
.consumer-metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.consumer-identity strong,
.consumer-identity span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.consumer-identity strong,
.consumer-metric strong {
  color: var(--insight-ink);
  font-size: 12px;
  font-weight: 650;
}

.consumer-identity span,
.consumer-signal > span,
.consumer-metric span,
.consumer-note {
  color: var(--insight-muted);
  font-size: 10px;
}

.consumer-signal {
  align-items: flex-start;
}

.consumer-note {
  margin: 0;
  padding: 9px 16px;
  border-top: 1px solid var(--insight-border);
  background: var(--insight-surface-muted);
}

@media (max-width: 1100px) {
  .consumer-row {
    min-width: 1040px;
  }

  .consumer-list {
    overflow-x: auto;
  }
}
</style>
