export interface GcWindow {
  minutes: number
  observedMs: number
  count: number
  timeMs: number
  overheadPercent: number
  sufficient: boolean
}
export interface MemoryTrend {
  available: boolean
  sufficient: boolean
  sustainedGrowth: boolean
  growthBytes: number
  points: { atMs: number; usedBytes: number }[]
  explanation: string
}
export interface DiagnosticSummary {
  atMs: number
  gc: GcWindow[]
  memory: MemoryTrend
  historyError?: string
}
export interface ObservationMetrics {
  fromMs: number
  toMs: number
  samples: number
  sufficient: boolean
  cpuPercent: number
  heapBytes: number
  gcPercent: number
  requests: number
  p95Ms?: number | null
}
export interface Observation {
  id: string
  pluginId: string
  displayName: string
  state: string
  createdAtMs: number
  fromMs: number
  toMs: number
  before: ObservationMetrics
  after?: ObservationMetrics
  message: string
}
export interface TaskSnapshot {
  atMs: number
  available: boolean
  coverage: string
  tasks: {
    id: string
    name: string
    state: string
    startedAtMs: number
    completedAtMs: number
    durationMs: number
    failureReason?: string
  }[]
  pools: {
    name: string
    active: number
    threads: number
    scheduledOrQueued: number
    readyQueued: number
    completed: number
    shutdown: boolean
  }[]
}
export interface Delivery {
  id: string
  channel: string
  state: string
  attempts: number
  nextAtMs: number
  createdAtMs: number
  updatedAtMs: number
  message: string
}
export interface ThreadSnapshot {
  sampledAtMs: number
  observedMs: number
  inspected: number
  total: number
  cpuAvailable: boolean
  threads: {
    id: number
    name: string
    state: string
    deadlocked: boolean
    cpuCorePercent: number
    lock?: string
    lockOwner?: string
    stack: string[]
  }[]
}
export interface LogResult {
  snapshotId: string
  capturedAtMs: number
  available: boolean
  sources: string[]
  entries: {
    level: string
    text: string
    loggerName?: string | null
    pluginIds: string[]
    attribution: 'logger' | 'stack' | 'logger-and-stack' | 'unknown'
  }[]
  exceptions: { signature: string; count: number }[]
  truncated: boolean
  page: number
  pageSize: number
  totalEntries: number
  hasNext: boolean
  plugins: { pluginId: string; displayName: string; indexed: boolean }[]
  pluginId: string
  unattributedEntries: number
  message: string
}

export interface OverheadSnapshot {
  fromMs: number
  toMs: number
  cpuCorePercent: number
  allocatedBytesPerMinute: number
  cpuAvailable: boolean
  allocationAvailable: boolean
  databaseReads: number
  databaseWrites: number
  operations: {
    id: string
    label: string
    calls: number
    failures: number
    averageMs: number
    maximumMs: number
    cpuTimeMs: number
    allocatedBytes: number
    cpuSamples: number
    allocationSamples: number
  }[]
  points: {
    atMs: number
    cpuTimeMs: number
    allocatedBytes: number
    databaseReads: number
    databaseWrites: number
  }[]
  coverage: string
}
export interface JfrSession {
  id: string
  state: string
  startedAtMs: number
  endsAtMs: number
  bytes: number
  message: string
  analysis?: {
    events: number
    truncated: boolean
    counts: Record<string, number>
    hotspots: { stack: string; samples: number }[]
  }
}
export interface Recordings {
  available: boolean
  sessions: JfrSession[]
}
import type { PluginRuntimeInfo, PluginConsumerItem, RequestRouteItem, TimeRange } from './insight'

export interface PluginDiagnosticDetail {
  plugin: PluginRuntimeInfo
  range: TimeRange
  fromMs: number
  toMs: number
  available: boolean
  pressure?: PluginConsumerItem | null
  timeline: PluginDiagnosticPoint[]
  routes: RequestRouteItem[]
  events: { atMs: number; type: string; title: string; summary: string }[]
  observations: Observation[]
  coverage: string
}
export interface PluginDiagnosticPoint {
  atMs: number
  intervalMs: number
  cpuTimeMs: number
  allocatedBytes: number
  maximumThreads: number
  resourceMinutes: number
  cpuSamples: number
  allocationSamples: number
  samples: number
  cpuObservedMinutes: number
  allocationObservedMinutes: number
  requests: number
  errors: number
  requestDurationMs: number
  maximumRequestMs: number
  requestMinutes: number
}

export interface PluginBaselineSummary {
  fromMs: number
  toMs: number
  observedMinutes: number
  expectedMinutes: number
  coveragePercent: number
  samples: number
  sufficient: boolean
  cpuCorePercent: number | null
  allocatedBytesPerMinute: number | null
  requests: number
  errorRatePercent: number | null
  averageRequestMs: number | null
  maximumThreads: number
  cpuSamples: number
  allocationSamples: number
  cpuObservedMinutes: number
  allocationObservedMinutes: number
}
export interface PluginBaselineComparison {
  available: boolean
  reference: string
  before: PluginBaselineSummary | null
  after: PluginBaselineSummary | null
  samplingComparable: boolean
  notices: string[]
}
