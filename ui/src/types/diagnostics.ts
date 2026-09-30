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
  available: boolean
  sources: string[]
  entries: { level: string; text: string }[]
  exceptions: { signature: string; count: number }[]
  truncated: boolean
  page: number
  pageSize: number
  totalEntries: number
  hasNext: boolean
  message: string
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
