import type { PluginConsumerItem, PluginRuntimeInfo, RuntimeView } from '@/types/insight'
import { formatBytes } from '@/utils/format'

export type IssueEnvironment = Pick<RuntimeView, 'haloVersion' | 'javaVersion' | 'osName'>

export interface PluginIssueDraft {
  title: string
  body: string
  issuesUrl?: string | null
}

export interface IssueTarget {
  url: string
  prefilled: boolean
}

// Keep metadata on one line and inert in Markdown, including mentions and quick actions.
function text(value?: string | null): string {
  return (value || '未知').replace(/[\r\n\t]/g, ' ').slice(0, 200)
    .replace(/[\\`*_{}\[\]<>|]/g, '\\$&').replace(/@/g, '@\u200b')
}

function number(value: number, unit = ''): string {
  return Number.isFinite(value) ? `${Math.max(0, value).toFixed(1)}${unit}` : '不可用'
}

function timestamp(value: number | undefined): string {
  if (!value || !Number.isFinite(value)) return '未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '未知' : date.toISOString()
}

function environmentLines(environment?: IssueEnvironment): string[] {
  return environment ? [
    `- Halo：${text(environment.haloVersion)}`,
    `- Java：${text(environment.javaVersion)}`,
    `- 操作系统：${text(environment.osName)}`,
  ] : []
}

const resources: Record<PluginConsumerItem['dominantResource'], string> = {
  cpu: 'CPU',
  'memory-allocation': '内存分配',
  'request-time': '请求耗时',
  threads: '线程',
}

export function consumerIssueDraft(
  consumer: PluginConsumerItem,
  environment?: IssueEnvironment,
  now = Date.now(),
): PluginIssueDraft {
  const name = text(consumer.displayName || consumer.pluginId)
  const confidence = { insufficient: '样本不足', low: '低', medium: '中等' }[consumer.confidence]
  return {
    title: `[运行洞察] ${name} 持续资源消耗反馈`,
    issuesUrl: consumer.issuesUrl,
    body: [
      '## 观察到的现象',
      `运行洞察观察到该插件的${resources[consumer.dominantResource]}消耗信号。请协助排查，以下指标为采样证据，并不证明该插件存在缺陷。`,
      '',
      '## 运行环境',
      `- 插件：${name}（${text(consumer.pluginId)}）`,
      `- 插件版本：${text(consumer.version)}`,
      `- 启用：${consumer.enabled ? '是' : '否'}；阶段：${text(consumer.phase)}`,
      ...environmentLines(environment),
      `- 报告生成时间（UTC）：${timestamp(now)}`,
      '',
      '## 指标证据',
      `- 查询区间（UTC）：${timestamp(consumer.fromMs)} 至 ${timestamp(consumer.toMs)}`,
      `- 已观察 ${consumer.observedMinutes} 分钟；活跃 ${consumer.activeMinutes} 分钟；位于消耗首位 ${consumer.topConsumerMinutes} 分钟`,
      `- 首位持续率：${number(consumer.persistencePercent, '%')}`,
      `- 压力评分：${number(consumer.pressureScore)} / 100；持续高消耗：${consumer.sustained ? '是' : '否'}；置信度：${confidence}`,
      `- CPU 份额（已检查 JVM 线程 CPU 增量）：${number(consumer.cpuSharePercent, '%')}`,
      `- 内存分配份额（已检查 JVM 线程分配增量）：${number(consumer.allocationSharePercent, '%')}`,
      `- 平均内存分配：${formatBytes(consumer.allocatedBytesPerMinute)}/min`,
      `- 插件请求耗时份额：${number(consumer.requestLoadSharePercent, '%')}`,
      `- 最大归因线程数：${consumer.attributedThreadsMax}`,
      '',
      '## 复现与影响（请补充）',
      '- 操作步骤：',
      '- 实际影响：',
      '- 预期行为：',
      '',
      '## 指标口径',
      'CPU 和内存分配通过有界线程采样、稳定的插件上下文类加载器归因；共享线程及短生命周期线程可能无法归因。CPU 份额不是整机 CPU 使用率，内存分配量不是当前驻留堆，也不能单独证明内存泄漏。',
      '',
      '由运行洞察生成；不包含站点地址、配置、请求正文或原始日志。',
    ].join('\n'),
  }
}

export function statusIssueDraft(
  plugin: PluginRuntimeInfo,
  environment?: IssueEnvironment,
  now = Date.now(),
): PluginIssueDraft {
  const name = text(plugin.displayName || plugin.name)
  return {
    title: `[运行洞察] ${name} 运行状态异常反馈`,
    issuesUrl: plugin.issuesUrl,
    body: [
      '## 观察到的现象',
      `Halo 报告插件阶段为 ${text(plugin.phase)}。请协助排查，当前状态不代表已定位根因。`,
      '',
      '## 运行环境',
      `- 插件：${name}（${text(plugin.name)}）`,
      `- 插件版本：${text(plugin.version)}`,
      `- 启用：${plugin.enabled ? '是' : '否'}；阶段：${text(plugin.phase)}`,
      ...environmentLines(environment),
      `- 最近成功启动时间：${text(plugin.lastStartTime)}`,
      `- 报告生成时间（UTC）：${timestamp(now)}`,
      '',
      '## 复现与影响（请补充）',
      '- 操作步骤：',
      '- 实际影响：',
      '- 预期行为：',
      '',
      '由运行洞察生成；不包含站点地址、配置、请求正文或原始日志。',
    ].join('\n'),
  }
}

export function hasPluginFailure(plugin: PluginRuntimeInfo): boolean {
  return /FAIL|ERROR/i.test(plugin.phase)
}

// Never navigate to executable URLs or expose credentials embedded in metadata.
export function issueTarget(draft: PluginIssueDraft): IssueTarget | undefined {
  if (!draft.issuesUrl) return undefined
  let url: URL
  try {
    url = new URL(draft.issuesUrl)
  } catch {
    return undefined
  }
  if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) {
    return undefined
  }
  const path = url.pathname.replace(/\/+$/, '')
  const github = url.hostname === 'github.com'
    && /^\/[^/]+\/[^/]+\/issues(?:\/new(?:\/choose)?)?$/.test(path)
  const gitlab = /^\/.+\/-\/(?:issues|work_items)(?:\/new)?$/.test(path)
  if (!github && !gitlab) return { url: url.href, prefilled: false }

  url.pathname = path.replace(/\/new(?:\/choose)?$/, '') + '/new'
  url.search = ''
  url.hash = ''
  const base = url.href
  url.searchParams.set(github ? 'title' : 'issue[title]', draft.title)
  url.searchParams.set(github ? 'body' : 'issue[description]', draft.body)
  if (gitlab) url.searchParams.set('issue[issue_type]', 'issue')
  // Leave the full report available for copy; don't silently truncate evidence.
  if (url.href.length > 8_000) return { url: base, prefilled: false }
  return { url: url.href, prefilled: true }
}
