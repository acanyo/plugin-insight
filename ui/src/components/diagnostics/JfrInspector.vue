<script setup lang="ts">
import { ref } from 'vue'
import { Dialog, VAlert, VButton } from '@halo-dev/components'
import { useDiagnosticResource } from '@/composables/insight/useDiagnosticResource'
import { downloadRecording, startRecording } from '@/api/diagnostics'
import type { Recordings } from '@/types/diagnostics'
import { errorMessage, formatBytes, formatDateTime } from '@/utils/format'
const { data, error, refresh } = useDiagnosticResource<Recordings>('recordings')
const seconds = ref(30),
  busy = ref(false),
  actionError = ref<string>()
const labels: Record<string, string> = {
  recording: '录制中',
  completed: '已完成',
  interrupted: '已中断',
  failed: '失败',
}
function start() {
  Dialog.warning({
    title: '开始短时 JFR 诊断',
    description: `采集 ${seconds.value} 秒的 CPU、分配和 GC 样本，会增加短时开销。记录包含类名和线程栈，仅诊断管理员可访问。`,
    confirmText: '开始录制',
    onConfirm: async () => {
      busy.value = true
      actionError.value = undefined
      try {
        await startRecording(seconds.value)
        await refresh()
      } catch (error) {
        actionError.value = errorMessage(error)
      } finally {
        busy.value = false
      }
    },
  })
}
async function download(id: string) {
  actionError.value = undefined
  try {
    await downloadRecording(id)
  } catch (error) {
    actionError.value = errorMessage(error)
  }
}
</script>
<template>
  <section class="insight-section diagnostic-section">
    <header>
      <div>
        <h2>按需 JFR 分析</h2>
        <p>白名单事件 · 同时仅一份录制 · 文件最多保留 3 份 / 3 天，自动清理</p>
      </div>
      <div class="diagnostic-controls">
        <label
          >时长<select v-model="seconds">
            <option :value="30">30 秒</option>
            <option :value="60">60 秒</option>
            <option :value="120">120 秒</option>
          </select></label
        ><VButton
          :loading="busy"
          :disabled="!data?.available || data.sessions.some((s) => s.state === 'recording')"
          @click="start"
          >开始录制</VButton
        >
      </div>
    </header>
    <VAlert
      v-if="error || actionError"
      type="error"
      :title="error || actionError || ''"
      :closable="false"
    />
    <p v-if="data && !data.available">当前 JVM 不支持 JFR。</p>
    <p v-if="!data?.sessions.length" class="diagnostic-empty">
      暂无录制。建议在高消耗持续发生时采集。
    </p>
    <article v-for="session in data?.sessions" :key="session.id" class="diagnostic-record">
      <header>
        <div>
          <h3>{{ labels[session.state] }} · {{ formatDateTime(session.startedAtMs) }}</h3>
          <p>{{ session.message }}</p>
        </div>
        <VButton v-if="session.state === 'completed'" size="sm" @click="download(session.id)"
          >下载 {{ formatBytes(session.bytes) }}</VButton
        >
      </header>
      <p v-if="session.state === 'recording'">
        预计 {{ formatDateTime(session.endsAtMs) }} 结束，随后自动分析。
      </p>
      <template v-if="session.analysis"
        ><p>
          分析 {{ session.analysis.events }} 个事件{{
            session.analysis.truncated ? '（已达到分析上限，完整内容见文件）' : ''
          }}
        </p>
        <details v-for="hotspot in session.analysis.hotspots" :key="hotspot.stack">
          <summary>{{ hotspot.samples }} 个样本 · {{ hotspot.stack.split('\n')[0] }}</summary>
          <pre>{{ hotspot.stack }}</pre>
        </details></template
      >
    </article>
  </section>
</template>
