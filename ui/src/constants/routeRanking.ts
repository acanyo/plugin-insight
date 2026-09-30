import type { RequestRouteSort, SelectOption } from '@/types/insight'

export type RouteSortColumn =
  'requests' | 'errors' | 'average' | 'p95' | 'p99' | 'maximum' | 'error-rate'

export interface RouteRankingPresentation {
  title: string
  description: string
  emptyTitle: string
  emptyDescription: string
  sortColumn: RouteSortColumn
}

export const ROUTE_SORT_OPTIONS: SelectOption<RequestRouteSort>[] = [
  { label: '慢接口', value: 'slow' },
  { label: 'P95', value: 'p95' },
  { label: 'P99', value: 'p99' },
  { label: '平均', value: 'average' },
  { label: '最大', value: 'max' },
  { label: '请求量', value: 'requests' },
  { label: '错误数', value: 'errors' },
  { label: '错误率', value: 'error-rate' },
]

export const ROUTE_RANKING_PRESENTATION: Record<RequestRouteSort, RouteRankingPresentation> = {
  slow: {
    title: '慢接口排行',
    description: '仅显示 P95 达到设置中慢请求阈值的归一化请求路径',
    emptyTitle: '没有检测到慢接口',
    emptyDescription: '当前时间范围内，所有接口的 P95 都低于慢请求阈值。',
    sortColumn: 'p95',
  },
  p95: {
    title: '接口 P95 排行',
    description: '所有归一化请求路径，按 P95 响应时间降序排列',
    emptyTitle: '暂无接口数据',
    emptyDescription: '所选时间范围内还没有采集到请求。',
    sortColumn: 'p95',
  },
  p99: {
    title: '接口 P99 排行',
    description: '所有归一化请求路径，按 P99 响应时间降序排列',
    emptyTitle: '暂无接口数据',
    emptyDescription: '所选时间范围内还没有采集到请求。',
    sortColumn: 'p99',
  },
  average: {
    title: '接口平均耗时排行',
    description: '所有归一化请求路径，按平均响应时间降序排列',
    emptyTitle: '暂无接口数据',
    emptyDescription: '所选时间范围内还没有采集到请求。',
    sortColumn: 'average',
  },
  max: {
    title: '接口峰值耗时排行',
    description: '所有归一化请求路径，按最大响应时间降序排列',
    emptyTitle: '暂无接口数据',
    emptyDescription: '所选时间范围内还没有采集到请求。',
    sortColumn: 'maximum',
  },
  requests: {
    title: '接口请求量排行',
    description: '所有归一化请求路径，按请求次数降序排列',
    emptyTitle: '暂无接口数据',
    emptyDescription: '所选时间范围内还没有采集到请求。',
    sortColumn: 'requests',
  },
  errors: {
    title: '接口错误数排行',
    description: '所有归一化请求路径，按错误次数降序排列',
    emptyTitle: '暂无接口数据',
    emptyDescription: '所选时间范围内还没有采集到请求。',
    sortColumn: 'errors',
  },
  'error-rate': {
    title: '接口错误率排行',
    description: '所有归一化请求路径，按错误率降序排列',
    emptyTitle: '暂无接口数据',
    emptyDescription: '所选时间范围内还没有采集到请求。',
    sortColumn: 'error-rate',
  },
}
