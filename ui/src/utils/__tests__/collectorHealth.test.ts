import { describe, expect, it } from 'vitest'

import type { OverviewResponse } from '@/types/insight'
import { resolveCollectorSignal } from '@/utils/collectorHealth'

function overview(
  collectorStatus = 'healthy',
  lastCollectionError: string | null = null,
): OverviewResponse {
  return {
    runtime: { lastCollectionError } as OverviewResponse['runtime'],
    dependencies: [
      {
        key: 'collector',
        name: '监控采集任务',
        status: collectorStatus,
        message: collectorStatus === 'healthy' ? '采样任务运行正常' : '采样任务未运行',
      },
    ],
  } as OverviewResponse
}

describe('resolveCollectorSignal', () => {
  it('uses the collector dependency as the visible sampling state', () => {
    expect(resolveCollectorSignal(overview())).toMatchObject({
      status: 'healthy',
      label: '实时采集',
    })
    expect(resolveCollectorSignal(overview('critical'))).toMatchObject({
      status: 'critical',
      label: '采集异常',
    })
  })

  it('prioritizes the latest collection error', () => {
    expect(resolveCollectorSignal(overview('healthy', 'SQLite 写入失败'))).toEqual({
      status: 'critical',
      label: '采集异常',
      message: 'SQLite 写入失败',
    })
  })

  it('reports an explicit waiting state before the first overview response', () => {
    expect(resolveCollectorSignal()).toMatchObject({ status: 'unknown', label: '等待采样' })
  })
})
