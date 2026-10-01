import { describe, expect, it } from 'vitest'

import { consumerIssueDraft, issueTarget, statusIssueDraft } from '@/utils/pluginIssue'

const consumer = {
  pluginId: 'plugin-demo',
  displayName: '演示插件',
  enabled: true,
  phase: 'STARTED',
  pressureScore: 72,
  pressureLevel: 'high' as const,
  dominantResource: 'cpu' as const,
  sustained: true,
  confidence: 'medium' as const,
  observedMinutes: 10,
  activeMinutes: 9,
  topConsumerMinutes: 7,
  persistencePercent: 70,
  cpuSharePercent: 40,
  allocationSharePercent: 20,
  requestLoadSharePercent: 10,
  allocatedBytesPerMinute: 1024,
  attributedThreadsMax: 3,
  version: '1.2.3',
  issuesUrl: 'https://github.com/acme/plugin-demo/issues',
  fromMs: 1_700_000_000_000,
  toMs: 1_700_000_600_000,
}

describe('plugin issue drafts', () => {
  it('creates a safe evidence report and prefills GitHub', () => {
    const draft = consumerIssueDraft(consumer, {
      haloVersion: '2.26.0',
      javaVersion: '21',
      osName: 'macOS',
    }, 1_700_000_600_000)
    const target = issueTarget(draft)

    expect(draft.body).toContain('插件版本：1.2.3')
    expect(draft.body).toContain('不包含站点地址、配置、请求正文或原始日志')
    expect(draft.body).not.toContain('https://github.com/acme')
    expect(target?.prefilled).toBe(true)
    expect(target?.url).toContain('title=')
    expect(target?.url).toContain('body=')
  })

  it('prefills GitLab issue fields and rejects unsafe URLs', () => {
    const draft = statusIssueDraft({
      name: 'plugin-demo',
      displayName: '演示插件',
      version: '1.2.3',
      issuesUrl: 'https://gitlab.example.com/group/plugin/-/issues',
      enabled: true,
      phase: 'FAILED',
      lastStartTime: null,
    })
    const target = issueTarget(draft)
    expect(target?.url).toContain('issue%5Btitle%5D=')
    expect(target?.url).toContain('issue%5Bdescription%5D=')
    expect(target?.url).toContain('issue%5Bissue_type%5D=issue')
    expect(issueTarget({ ...draft, issuesUrl: 'javascript:alert(1)' })).toBeUndefined()
  })
})
