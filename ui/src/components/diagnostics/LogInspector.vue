<script setup lang="ts">
import { computed, ref } from 'vue'
import { VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import type { LogResult } from '@/types/diagnostics'
const level = ref('all'),
  keyword = ref(''),
  source = ref(0),
  page = ref(1),
  pageSize = ref(20)
const { data, error, loading, refresh } = useDiagnosticResource<LogResult>(
  'diagnostic-logs',
  false,
  () => ({
    level: level.value,
    keyword: keyword.value,
    source: source.value,
    page: page.value,
    pageSize: pageSize.value,
  }),
)

const totalPages = computed(() =>
  Math.max(1, Math.ceil((data.value?.totalEntries ?? 0) / pageSize.value)),
)

function queryLogs() {
  page.value = 1
  void refresh()
}

function movePage(nextPage: number) {
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  void refresh()
}
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>日志与异常聚合</h2>
        <p>读取 Halo 已配置的文件日志，按需查询</p>
      </div>
    </header>
    <form class="diagnostic-controls" @submit.prevent="queryLogs">
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
    </form>
    <VAlert v-if="error" type="error" :title="error" :closable="false" />
    <p>{{ data?.message }}</p>
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
