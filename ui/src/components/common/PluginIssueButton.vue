<script setup lang="ts">
import { Toast, VButton, VModal } from '@halo-dev/components'
import { computed, ref } from 'vue'

import type { PluginConsumerItem, PluginRuntimeInfo } from '@/types/insight'
import {
  consumerIssueDraft,
  issueTarget,
  statusIssueDraft,
  type IssueEnvironment,
  type PluginIssueDraft,
} from '@/utils/pluginIssue'

const props = defineProps<{
  plugin?: PluginRuntimeInfo
  consumer?: PluginConsumerItem
  environment?: IssueEnvironment
}>()

const draft = ref<PluginIssueDraft>()
const target = computed(() => draft.value ? issueTarget(draft.value) : undefined)
const copying = ref(false)

function prepare() {
  if (props.consumer) draft.value = consumerIssueDraft(props.consumer, props.environment)
  else if (props.plugin) draft.value = statusIssueDraft(props.plugin, props.environment)
}

async function copyReport() {
  if (!draft.value) return
  copying.value = true
  try {
    await navigator.clipboard.writeText(`${draft.value.title}\n\n${draft.value.body}`)
    Toast.success('Issue 描述已复制')
  } catch {
    Toast.warning('复制未完成，请选中下方描述手动复制')
  } finally {
    copying.value = false
  }
}
</script>

<template>
  <VButton size="sm" @click="prepare">反馈 Issue</VButton>
  <VModal v-if="draft" :width="720" title="反馈插件问题" @close="draft = undefined">
    <div class="issue-draft">
      <p>核对并补充描述后打开插件的 Issue 页面，在目标平台完成提交。</p>
      <label for="plugin-issue-title">标题</label>
      <input id="plugin-issue-title" v-model="draft.title" maxlength="200" />
      <label for="plugin-issue-body">描述</label>
      <textarea id="plugin-issue-body" v-model="draft.body" rows="16" maxlength="20000" />
      <p v-if="target" class="issue-target">目标：{{ target.url.split('?')[0] }}</p>
      <p v-if="target && !target.prefilled">
        此页面无法自动带入完整描述，请先复制后粘贴到 Issue 中。
      </p>
      <p v-else-if="!target">插件未提供有效的 Issue 地址，可以复制描述联系作者。</p>
    </div>
    <template #footer>
      <div class="issue-actions">
        <VButton size="sm" :loading="copying" @click="copyReport">复制描述</VButton>
        <a
          v-if="target"
          class="issue-link"
          :href="target.url"
          target="_blank"
          rel="noopener noreferrer"
          referrerpolicy="no-referrer"
        >打开 Issue 页面</a>
      </div>
    </template>
  </VModal>
</template>

<style scoped>
.issue-draft {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.issue-draft p {
  margin: 0;
  color: var(--insight-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.issue-draft label {
  color: var(--insight-ink);
  font-size: 12px;
  font-weight: 600;
}

.issue-draft input,
.issue-draft textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--insight-border);
  border-radius: 4px;
  color: var(--insight-ink);
  background: var(--insight-surface);
  font: inherit;
  font-size: 12px;
}

.issue-draft textarea {
  max-height: 45vh;
  resize: vertical;
}

.issue-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.issue-link {
  padding: 7px 12px;
  border-radius: 4px;
  color: white;
  background: var(--insight-primary);
  font-size: 12px;
  font-weight: 600;
}
</style>
