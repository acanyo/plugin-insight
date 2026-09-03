<script setup lang="ts">
import SectionState from '@/components/common/SectionState.vue'
import DependencyStatusSection from '@/components/overview/DependencyStatusSection.vue'
import JvmResourceSection from '@/components/overview/JvmResourceSection.vue'
import OverviewMetricsGrid from '@/components/overview/OverviewMetricsGrid.vue'
import OverviewTrends from '@/components/overview/OverviewTrends.vue'
import PluginStatusSection from '@/components/overview/PluginStatusSection.vue'
import StatusCodeSection from '@/components/overview/StatusCodeSection.vue'
import StorageSection from '@/components/overview/StorageSection.vue'
import type { OverviewResponse, TrendsResponse } from '@/types/insight'

defineProps<{
  overview?: OverviewResponse
  trends?: TrendsResponse
  loading: boolean
  error?: string
  trendsLoading: boolean
  trendsError?: string
  clearingData: boolean
}>()

const emit = defineEmits<{
  retry: []
  clearMonitoringData: []
}>()
</script>

<template>
  <SectionState
    :loading="loading && !overview"
    :error="!overview ? error : undefined"
    :empty="!loading && !error && !overview"
    empty-title="尚未收到运行指标"
    empty-description="采集器产生第一批样本后会显示在这里。"
    @retry="emit('retry')"
  >
    <div v-if="overview" class="overview-panel">
      <OverviewMetricsGrid :overview="overview" />

      <OverviewTrends
        :trends="trends"
        :loading="trendsLoading"
        :error="trendsError"
        @retry="emit('retry')"
      />

      <div class="overview-panel__status-grid">
        <DependencyStatusSection
          :dependencies="overview.dependencies"
          @retry="emit('retry')"
        />
        <StatusCodeSection
          :status-codes="overview.requests.statusCodes"
          @retry="emit('retry')"
        />
        <PluginStatusSection
          class="overview-panel__plugin-status"
          :plugins="overview.plugins"
          :active-alerts="overview.activeAlerts"
        />
      </div>

      <div class="overview-panel__resource-grid">
        <JvmResourceSection :overview="overview" />
        <StorageSection
          :overview="overview"
          :clearing-data="clearingData"
          @clear-monitoring-data="emit('clearMonitoringData')"
        />
      </div>
    </div>
  </SectionState>
</template>

<style scoped>
.overview-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.overview-panel__status-grid,
.overview-panel__resource-grid {
  display: grid;
  min-width: 0;
  gap: 14px;
}

.overview-panel__status-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.overview-panel__resource-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 1250px) {
  .overview-panel__status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-panel__plugin-status {
    grid-column: 1 / -1;
  }
}

@media (max-width: 820px) {
  .overview-panel__status-grid,
  .overview-panel__resource-grid {
    grid-template-columns: 1fr;
  }

  .overview-panel__plugin-status {
    grid-column: auto;
  }
}
</style>
