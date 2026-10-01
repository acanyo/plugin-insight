<script setup lang="ts">
import { VButton } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import RiPauseCircleLine from '~icons/ri/pause-circle-line'

import StatusBadge from '@/components/common/StatusBadge.vue'
import PluginIssueButton from '@/components/common/PluginIssueButton.vue'
import { HALO_PLUGIN_MANAGE_PERMISSION, INSIGHT_MANAGE_PERMISSION } from '@/constants/permissions'
import type { HealthStatus, PluginConsumerItem } from '@/types/insight'
import type { IssueEnvironment } from '@/utils/pluginIssue'
import { formatBytes, formatPercent } from '@/utils/format'

defineProps<{
  consumer?: PluginConsumerItem | null
  resourceSamplingEnabled: boolean
  disabling: boolean
  environment?: IssueEnvironment
}>()

const emit = defineEmits<{
  disable: [consumer: PluginConsumerItem]
}>()

const resourceLabels: Record<PluginConsumerItem['dominantResource'], string> = {
  cpu: 'CPU',
  'memory-allocation': '内存分配',
  'request-time': '请求耗时',
  threads: '线程',
}

function pressureTone(consumer: PluginConsumerItem): HealthStatus {
  if (!consumer.sustained) return 'unknown'
  if (consumer.pressureLevel === 'high') return 'critical'
  return 'warning'
}

function canDisable(consumer: PluginConsumerItem): boolean {
  return consumer.sustained && consumer.enabled && consumer.pluginId !== 'insight'
}
</script>

<template>
  <section class="plugin-action">
    <header>
      <div>
        <span class="section-eyebrow">近 1 小时</span>
        <h2>最高消耗插件</h2>
      </div>
      <span>按持续压力排序</span>
    </header>

    <div v-if="consumer" class="consumer-content">
      <div class="consumer-identity">
        <div>
          <StatusBadge
            :tone="pressureTone(consumer)"
            :label="consumer.sustained ? '持续高消耗' : '暂未持续'"
          />
          <strong>{{ consumer.displayName || consumer.pluginId }}</strong>
          <span class="insight-mono">{{ consumer.pluginId }}</span>
        </div>
        <p v-if="resourceSamplingEnabled">
          主要消耗为{{
            resourceLabels[consumer.dominantResource]
          }}；内存数值是分配速率，不代表该插件当前独占的 JVM 堆。
        </p>
        <p v-else>
          当前未启用插件资源归因，排行主要依据请求耗时；暂时不能判断插件的 CPU、线程或内存分配消耗。
        </p>
      </div>

      <div class="consumer-evidence">
        <div>
          <span>压力评分</span>
          <strong class="insight-mono">{{ consumer.pressureScore.toFixed(0) }}</strong>
        </div>
        <div>
          <span>插件 CPU 份额</span>
          <strong class="insight-mono">{{ formatPercent(consumer.cpuSharePercent) }}</strong>
        </div>
        <div>
          <span>内存分配</span>
          <strong class="insight-mono"
            >{{ formatBytes(consumer.allocatedBytesPerMinute) }}/min</strong
          >
        </div>
        <div>
          <span>首位持续率</span>
          <strong class="insight-mono">{{ formatPercent(consumer.persistencePercent) }}</strong>
        </div>
      </div>

      <div class="consumer-action">
        <VButton
          v-if="canDisable(consumer) && utils.permission.has([HALO_PLUGIN_MANAGE_PERMISSION, INSIGHT_MANAGE_PERMISSION], false)"
          type="danger"
          size="sm"
          :loading="disabling"
          @click="emit('disable', consumer)"
        >
          <template #icon><RiPauseCircleLine /></template>
          停用并观察
        </VButton>
        <span v-if="canDisable(consumer)">需要 Halo 插件管理和 Insight 管理权限，执行前会再次确认。</span>
        <span v-else-if="consumer.pluginId === 'insight'">Insight 不会停用自身。</span>
        <span v-else-if="!consumer.sustained">当前证据不足，不建议停用插件。</span>
        <span v-else>插件当前未启用。</span>
        <PluginIssueButton
          v-if="consumer.sustained"
          :consumer="consumer"
          :environment="environment"
        />
      </div>
    </div>

    <div v-else class="consumer-empty">
      <strong>暂无可归因的插件消耗</strong>
      <p v-if="resourceSamplingEnabled">资源归因已经启用，请等待至少 3 分钟样本。</p>
      <p v-else>请先在设置中启用插件资源归因，再等待至少 3 分钟样本。</p>
    </div>
  </section>
</template>

<style scoped>
.plugin-action {
  overflow: hidden;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
}

.plugin-action > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--insight-border);
  background: var(--insight-surface-muted);
}

.plugin-action header > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.plugin-action h2 {
  margin: 0;
  color: var(--insight-ink);
  font-size: 13px;
  font-weight: 650;
}

.plugin-action header span,
.section-eyebrow,
.consumer-identity span,
.consumer-action span,
.consumer-empty p {
  color: var(--insight-subtle);
  font-size: 11px;
}

.consumer-content {
  display: grid;
  align-items: center;
  grid-template-columns: minmax(200px, 1fr) minmax(300px, 1.25fr) auto;
  gap: 16px;
  padding: 13px 14px;
}

.consumer-identity > div {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.consumer-identity strong {
  color: var(--insight-ink);
  font-size: 13px;
  font-weight: 650;
}

.consumer-identity p,
.consumer-empty p {
  margin: 7px 0 0;
  color: var(--insight-muted);
  font-size: 11px;
  line-height: 1.5;
}

.consumer-evidence {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.consumer-evidence > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.consumer-evidence span {
  color: var(--insight-subtle);
  font-size: 10px;
}

.consumer-evidence strong {
  overflow: hidden;
  color: var(--insight-ink);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.consumer-action {
  display: flex;
  max-width: 150px;
  align-items: flex-end;
  flex-direction: column;
  gap: 5px;
  text-align: right;
}

.consumer-empty {
  padding: 16px;
}

.consumer-empty strong {
  color: var(--insight-ink);
  font-size: 12px;
}

@media (max-width: 760px) {
  .consumer-content {
    grid-template-columns: 1fr;
  }

  .consumer-action {
    max-width: none;
    align-items: flex-start;
    text-align: left;
  }
}

@media (max-width: 480px) {
  .consumer-evidence {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
