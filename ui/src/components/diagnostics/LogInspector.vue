<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { LogResult } from '@/types/diagnostics'
import { formatDateTime } from '@/utils/format'
const props = withDefaults(defineProps<{ initialPluginId?: string }>(), { initialPluginId: '' })
const level = ref('all'),
  keyword = ref(''),
  source = ref(0),
  pluginId = ref(props.initialPluginId),
  page = ref(1),
  pageSize = ref(20)
const snapshotId = ref('')
const appliedKeyword = ref('')
const mode = ref<'query' | 'page' | 'incremental'>('query')
const { data, error, loading, refresh } = useDiagnosticResource<LogResult>(
  'diagnostic-logs',
  false,
  () => ({
    level: level.value,
    keyword: appliedKeyword.value,
    source: source.value,
    pluginId: pluginId.value,
    page: page.value,
    pageSize: pageSize.value,
    snapshotId: snapshotId.value,
    mode: mode.value,
  }),
)

const totalPages = computed(() =>
  Math.max(1, Math.ceil((data.value?.totalEntries ?? 0) / pageSize.value)),
)

const pluginNames = computed(
  () => new Map(data.value?.plugins.map((plugin) => [plugin.pluginId, plugin.displayName]) ?? []),
)

const selectedPlugin = computed(() =>
  data.value?.plugins.find((plugin) => plugin.pluginId === data.value?.pluginId),
)

const attributionLabels = {
  logger: '插件日志',
  stack: '异常栈关联',
  'logger-and-stack': '日志与异常栈关联',
  unknown: '未识别插件归属',
} as const

watch([pluginId, source, level, pageSize], queryLogs)

watch(data, (value) => {
  if (value) snapshotId.value = value.snapshotId
})

function queryLogs() {
  page.value = 1
  snapshotId.value = ''
  appliedKeyword.value = keyword.value
  mode.value = 'query'
  void refresh()
}

function movePage(nextPage: number) {
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  mode.value = 'page'
  void refresh()
}

function readNewLogs() {
  if (loading.value || !snapshotId.value) return
  page.value = 1
  mode.value = 'incremental'
  void refresh()
}
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>日志与异常聚合</h2>
        <p>选择指定插件查看日志，支持级别、关键词筛选与异常聚合，按需查询</p>
      </div>
    </header>
    <form class="diagnostic-controls" @submit.prevent="queryLogs">
      <label
        >插件<select v-model="pluginId" aria-label="日志插件">
          <option value="">全部日志</option>
          <option v-for="plugin in data?.plugins" :key="plugin.pluginId" :value="plugin.pluginId">
            {{ plugin.displayName }}（{{ plugin.pluginId }}）
          </option>
        </select></label
      >
      <label
        >来源<select v-model="source">
          <option v-for="(name, index) in data?.sources" :key="index" :value="index">
            {{ name }}
          </option>
        </select></label
      >
      <label
        >级别<select v-model="level">
          <option value="all">全部</option>
          <option>ERROR</option>
          <option>WARN</option>
          <option>INFO</option>
          <option>DEBUG</option>
          <option>TRACE</option>
        </select></label
      >
      <label
        >关键词<input v-model="keyword" maxlength="100" placeholder="异常类名、接口或消息"
      /></label>
      <label
        >每页<select v-model.number="pageSize">
          <option :value="20">20 条</option>
          <option :value="50">50 条</option>
          <option :value="100">100 条</option>
        </select></label
      >
      <VButton :loading="loading" @click="queryLogs">查询</VButton>
      <VButton :disabled="loading || !snapshotId" @click="readNewLogs">读取新增日志</VButton>
    </form>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <p>{{ data?.message }}</p>
    <p v-if="data?.capturedAtMs">快照时间：{{ formatDateTime(data.capturedAtMs) }}</p>
    <p v-if="selectedPlugin && !selectedPlugin.indexed">
      当前插件的类索引不可用或没有可识别的类，暂无法关联其日志。可切换到全部日志按关键词查询。
    </p>
    <p v-if="data && data.unattributedEntries > 0">
      本次读取有 {{ data.unattributedEntries }} 条日志无法识别插件归属，可切换到全部日志检索。
    </p>
    <details v-if="data?.exceptions.length">
      <summary>异常堆栈聚合 · {{ data.exceptions.length }} 组</summary>
      <article v-for="item in data.exceptions" :key="item.signature">
        <strong>{{ item.count }} 次</strong>
        <pre>{{ item.signature }}</pre>
      </article>
    </details>
    <p v-if="data?.available && !data.entries.length" class="diagnostic-empty">
      当前读取范围内没有匹配日志。
    </p>
    <details v-for="(entry, index) in data?.entries" :key="index" class="diagnostic-record">
      <summary>{{ entry.level }} · {{ entry.text.split('\n')[0] }}</summary>
      <p>
        {{ attributionLabels[entry.attribution] }}
        <template v-if="entry.pluginIds.length">
          · {{ entry.pluginIds.map((id) => pluginNames.get(id) ?? id).join('、') }}
        </template>
      </p>
      <pre>{{ entry.text }}</pre>
    </details>
    <nav
      v-if="data?.available && data.totalEntries > 0"
      class="diagnostic-pagination"
      aria-label="日志分页"
    >
      <VButton :disabled="page <= 1 || loading" @click="movePage(page - 1)">上一页</VButton>
      <span>第 {{ data.page }} / {{ totalPages }} 页 · 共 {{ data.totalEntries }} 条</span>
      <VButton :disabled="!data.hasNext || loading" @click="movePage(page + 1)">下一页</VButton>
    </nav>
  </section>
</template>
