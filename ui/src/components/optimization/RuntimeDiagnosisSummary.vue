<script setup lang="ts">
import StatusBadge from '@/components/common/StatusBadge.vue'
import type { HealthStatus, RuntimeDiagnosis } from '@/types/insight'
import { formatBytes, formatNumber, formatPercent } from '@/utils/format'

defineProps<{ diagnosis: RuntimeDiagnosis }>()

const levelLabels: Record<HealthStatus, string> = {
  healthy: '运行平稳',
  warning: '存在压力',
  critical: '需要处理',
  unknown: '等待采样',
}

function findingTone(severity: string): HealthStatus {
  if (severity === 'critical' || severity === 'high') return 'critical'
  if (severity === 'warning' || severity === 'medium') return 'warning'
  return 'healthy'
}
</script>

<template>
  <section class="runtime-diagnosis">
    <header class="diagnosis-header">
      <div>
        <StatusBadge :tone="diagnosis.level" :label="levelLabels[diagnosis.level]" />
        <h2>JVM 与 Halo 进程诊断</h2>
        <p>{{ diagnosis.summary }}</p>
      </div>
      <span class="diagnosis-window">实时状态</span>
    </header>

    <div class="runtime-metrics">
      <div>
        <span>JVM 堆</span>
        <strong class="insight-mono">{{ formatPercent(diagnosis.heapUsedPercent) }}</strong>
        <small>
          {{ formatBytes(diagnosis.heapUsedBytes) }} / {{ formatBytes(diagnosis.heapMaxBytes) }}
        </small>
      </div>
      <div>
        <span>Halo 进程 CPU</span>
        <strong class="insight-mono">{{ formatPercent(diagnosis.processCpuPercent) }}</strong>
        <small>系统 {{ formatPercent(diagnosis.systemCpuPercent) }}</small>
      </div>
      <div>
        <span>近期 GC 时间占比</span>
        <strong class="insight-mono">{{ diagnosis.gcWindowSufficient ? formatPercent(diagnosis.gcOverheadPercent) : '采样不足' }}</strong>
        <small>本次启动累计 {{ formatNumber(diagnosis.gcCount) }} 次</small>
      </div>
      <div>
        <span>活动线程</span>
        <strong class="insight-mono">{{ formatNumber(diagnosis.liveThreads) }}</strong>
        <small>
          {{ diagnosis.deadlockedThreads ? `死锁 ${diagnosis.deadlockedThreads}` : '未发现死锁' }}
        </small>
      </div>
    </div>

    <div class="finding-list">
      <article v-for="finding in diagnosis.findings" :key="finding.id">
        <StatusBadge :tone="findingTone(finding.severity)" />
        <div>
          <strong>{{ finding.title }}</strong>
          <p>{{ finding.description }}</p>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.runtime-diagnosis {
  overflow: hidden;
  border: 1px solid var(--insight-border);
  border-radius: 6px;
  background: var(--insight-surface);
}

.diagnosis-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--insight-border);
  background: var(--insight-surface-muted);
}

.diagnosis-header > div {
  min-width: 0;
}

.diagnosis-header h2 {
  margin: 7px 0 3px;
  color: var(--insight-ink);
  font-size: 14px;
  font-weight: 650;
}

.diagnosis-header p,
.finding-list p {
  margin: 0;
  color: var(--insight-muted);
  font-size: 12px;
  line-height: 1.55;
}

.diagnosis-window {
  flex: 0 0 auto;
  color: var(--insight-subtle);
  font-size: 11px;
}

.runtime-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid var(--insight-border);
}

.runtime-metrics > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  padding: 11px 13px;
  border-right: 1px solid var(--insight-border);
}

.runtime-metrics > div:last-child {
  border-right: 0;
}

.runtime-metrics span,
.runtime-metrics small {
  overflow: hidden;
  color: var(--insight-subtle);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-metrics strong {
  color: var(--insight-ink);
  font-size: 18px;
  font-weight: 700;
}

.finding-list {
  padding: 2px 16px;
}

.finding-list article {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--insight-border);
}

.finding-list article:last-child {
  border-bottom: 0;
}

.finding-list strong {
  display: block;
  margin-bottom: 2px;
  color: var(--insight-ink);
  font-size: 12px;
  font-weight: 650;
}

@media (max-width: 640px) {
  .runtime-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .runtime-metrics > div:nth-child(2) {
    border-right: 0;
  }

  .runtime-metrics > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--insight-border);
  }
}
</style>
