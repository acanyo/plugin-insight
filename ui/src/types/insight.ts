export type HealthStatus = 'healthy' | 'warning' | 'critical' | 'unknown'

export type TimeRange = '15m' | '30m' | '1h' | '6h' | '24h' | '7d' | '30d'
export type DashboardTab = 'overview' | 'performance' | 'alerts' | 'events' | 'diagnostics'
export type Severity = 'info' | 'warning' | 'critical'
export type AlertStatus = 'open' | 'acknowledged' | 'resolved' | 'all'
export type PluginSort = 'cpu' | 'memory' | 'threads' | 'load' | 'latency' | 'errors' | 'requests'
export type RequestRouteSort =
  'slow' | 'p95' | 'p99' | 'average' | 'max' | 'requests' | 'errors' | 'error-rate'

export interface SelectOption<T extends string> {
  label: string
  value: T
}

export const TIME_RANGE_OPTIONS: SelectOption<TimeRange>[] = [
  { label: '15 分钟', value: '15m' },
  { label: '30 分钟', value: '30m' },
  { label: '1 小时', value: '1h' },
  { label: '6 小时', value: '6h' },
  { label: '24 小时', value: '24h' },
  { label: '7 天', value: '7d' },
  { label: '30 天', value: '30d' },
]

export const DASHBOARD_TAB_OPTIONS: SelectOption<DashboardTab>[] = [
  { label: '总览', value: 'overview' },
  { label: '性能', value: 'performance' },
  { label: '告警', value: 'alerts' },
  { label: '事件', value: 'events' },
  { label: '诊断', value: 'diagnostics' },
]

export interface RuntimeView {
  running: boolean
  uptimeMs: number
  haloVersion: string
  javaVersion: string
  osName: string
  processCpuPercent: number
  systemCpuPercent: number
  systemMemoryUsedBytes: number
  systemMemoryTotalBytes: number
  diskUsedBytes: number
  diskTotalBytes: number
  lastCollectedAtMs: number
  lastCollectionError?: string | null
}

export interface JvmView {
  heapUsedBytes: number
  heapMaxBytes: number
  heapUsedPercent: number
  nonHeapUsedBytes: number
  gcCount: number
  gcPauseMs: number
  liveThreads: number
  daemonThreads: number
  peakThreads: number
  deadlockedThreads: number
}

export interface RequestView {
  windowFromMs: number
  windowToMs: number
  requestCount: number
  errorCount: number
  errorRatePercent: number
  averageLatencyMs: number
  p95Ms: number
  p99Ms: number
  maximumLatencyMs: number
  statusCodes: Record<string, number>
}

export interface StorageView {
  state: string
  message: string
  available: boolean
  writePaused: boolean
  sizeBytes: number
  maxSizeBytes: number
  checkedAtMs: number
  failureCount: number
}

export interface DependencyStatus {
  key: string
  name: string
  status: HealthStatus | string
  message: string
  providers?: DependencyProvider[]
}

export interface DependencyProvider {
  pluginId: string
  displayName?: string | null
}

export interface PluginRuntimeInfo {
  name: string
  displayName?: string | null
  version?: string | null
  enabled: boolean
  phase: string
  lastStartTime?: string | null
}

export interface AlertIncident {
  id: string
  ruleId: string
  status: string
  severity: string
  title: string
  summary: string
  fingerprint: string
  triggeredAtMs: number
  updatedAtMs: number
  resolvedAtMs?: number | null
  occurrenceCount: number
  lastValue?: number | null
  thresholdValue?: number | null
  detailsJson?: string | null
}

export interface AlertPage {
  items: AlertIncident[]
  nextCursor?: string | null
}

export interface OverviewResponse {
  generatedAtMs: number
  health: HealthStatus | string
  runtime: RuntimeView
  jvm: JvmView
  requests: RequestView
  storage: StorageView
  dependencies: DependencyStatus[]
  plugins: PluginRuntimeInfo[]
  activeAlerts: AlertIncident[]
}

export interface SystemTrendPoint {
  timestampMs: number
  processCpuPercent: number
  systemCpuPercent: number
  systemLoadAverage: number
  heapUsedBytes: number
  heapMaxBytes: number
  nonHeapUsedBytes: number
  memoryUsedBytes: number
  memoryTotalBytes: number
  diskUsedBytes: number
  diskTotalBytes: number
  liveThreads: number
  daemonThreads: number
  peakThreads: number
  deadlockedThreads: number
  gcCountDelta: number
  gcPauseMs: number
}

export interface RequestTrendPoint {
  timestampMs: number
  requestCount: number
  requestsPerMinute: number
  clientErrorCount: number
  clientErrorRatePercent: number
  serverErrorCount: number
  serverErrorRatePercent: number
  errorCount: number
  errorRatePercent: number
  averageLatencyMs: number
  p95Ms: number
  p99Ms: number
}

export interface TrendsResponse {
  range: string
  fromMs: number
  toMs: number
  system: SystemTrendPoint[]
  requests: RequestTrendPoint[]
}

export interface PluginRankingItem {
  pluginId: string
  displayName?: string | null
  enabled: boolean
  phase: string
  estimated: boolean
  loadSharePercent: number
  requestCount: number
  errorCount: number
  errorRatePercent: number
  averageResponseMs: number
  maximumResponseMs: number
  resourceAvailability: string
  resourceConfidence: string
  resourceAttributionMethod: string
  cpuTimeAvailable: boolean
  allocatedMemoryAvailable: boolean
  cpuSharePercent: number
  allocationSharePercent: number
  cpuTimeMs: number
  allocatedMemoryBytes: number
  activeThreadSamples: number
  attributedThreadsMax: number
  cpuAttributionCoveragePercent: number
  allocationAttributionCoveragePercent: number
}

export type ConsumerPressureLevel = 'none' | 'low' | 'medium' | 'high'
export type ConsumerResource = 'cpu' | 'memory-allocation' | 'request-time' | 'threads'

export interface PluginConsumerItem {
  pluginId: string
  displayName: string
  enabled: boolean
  phase: string
  pressureScore: number
  pressureLevel: ConsumerPressureLevel
  dominantResource: ConsumerResource
  sustained: boolean
  confidence: 'insufficient' | 'low' | 'medium'
  observedMinutes: number
  activeMinutes: number
  topConsumerMinutes: number
  persistencePercent: number
  cpuSharePercent: number
  allocationSharePercent: number
  requestLoadSharePercent: number
  allocatedBytesPerMinute: number
  attributedThreadsMax: number
}

export interface OptimizationChange {
  id: string
  title: string
  description: string
  automatic: boolean
  applied: boolean
}

export interface RuntimeFinding {
  id: string
  severity: string
  title: string
  description: string
}

export interface RuntimeDiagnosis {
  gcWindowSufficient?: boolean
  level: HealthStatus
  summary: string
  heapUsedBytes: number
  heapMaxBytes: number
  heapUsedPercent: number
  processCpuPercent: number
  systemCpuPercent: number
  gcCount: number
  gcTimeMs: number
  gcOverheadPercent: number
  liveThreads: number
  deadlockedThreads: number
  findings: RuntimeFinding[]
}

export interface OptimizationStatus {
  mode: 'standard' | 'low-overhead'
  enabled: boolean
  canApply: boolean
  appliedAtMs: number
  updatedAtMs: number
  configuredSamplingSeconds: number
  effectiveSamplingSeconds: number
  optimizedSamplingSeconds: number
  configuredPluginThreadLimit: number
  effectivePluginThreadLimit: number
  optimizedPluginThreadLimit: number
  pluginResourceSamplingEnabled: boolean
  runtime: RuntimeDiagnosis
  changes: OptimizationChange[]
  topConsumer?: PluginConsumerItem | null
}

export interface RequestRouteItem {
  scope: string
  route: string
  method: string
  requestCount: number
  errorCount: number
  errorRatePercent: number
  averageLatencyMs: number
  p95Ms: number
  p99Ms: number
  maximumLatencyMs: number
}

export interface MonitorEvent {
  id: string
  occurredAtMs: number
  type: string
  severity: string
  source: string
  title: string
  summary: string
  fingerprint: string
  firstSeenAtMs: number
  lastSeenAtMs: number
  occurrenceCount: number
  detailsJson?: string | null
}

export interface CursorPage<T> {
  items: T[]
  nextCursor?: string | null
}

export interface EventQuery {
  cursor?: string
  limit: number
  type?: string
  severity?: Severity
}

export interface NotificationResult {
  channel: string
  sent: boolean
  message: string
}

export interface ClearDataResponse {
  scope: string
  deletedRows: number
}

export interface HealthRailItem {
  key: 'runtime' | 'storage' | 'requests' | 'alerts'
  label: string
  status: HealthStatus
  value: string
  detail: string
  explanation?: string
}

export interface ChartPoint {
  timestampMs: number
  [key: string]: number
}
