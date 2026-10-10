import type { AlertIncident } from '@/types/insight'

export interface AlertScene {
  capturedAtMs: number
  sampledAtMs: number
  processCpuPercent: number | null
  heapUsedPercent: number | null
  gcPercent: number | null
  requestCount: number
  p95Ms: number | null
  requestFromMs: number
  requestToMs: number
  plugins: {
    pluginId: string
    displayName: string
    pressureScore: number
    cpuSharePercent: number
    allocatedBytesPerMinute: number
    sustained: boolean
  }[]
  routes: { method: string; route: string; requests: number; errors: number; p95Ms: number }[]
  coverage: string
}

const finite = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value)
const record = (value: unknown): value is Record<string, unknown> =>
  !!value && typeof value === 'object' && !Array.isArray(value)
const optionalNumber = (value: unknown) => value == null || finite(value)

export function alertScene(alert: AlertIncident): AlertScene | undefined {
  if (!alert.detailsJson || alert.detailsJson.length > 262_144) return
  try {
    const payload = JSON.parse(alert.detailsJson) as unknown
    if (!record(payload) || !record(payload.scene)) return
    const scene = payload.scene
    if (
      !['capturedAtMs', 'sampledAtMs', 'requestCount', 'requestFromMs', 'requestToMs'].every(
        (key) => finite(scene[key]),
      ) ||
      !['processCpuPercent', 'heapUsedPercent', 'gcPercent', 'p95Ms'].every((key) =>
        optionalNumber(scene[key]),
      ) ||
      typeof scene.coverage !== 'string' ||
      !Array.isArray(scene.plugins) ||
      !Array.isArray(scene.routes)
    )
      return
    return {
      ...scene,
      plugins: scene.plugins
        .filter(
          (plugin) =>
            record(plugin) &&
            typeof plugin.pluginId === 'string' &&
            typeof plugin.displayName === 'string' &&
            ['pressureScore', 'cpuSharePercent', 'allocatedBytesPerMinute'].every((key) =>
              finite(plugin[key]),
            ) &&
            typeof plugin.sustained === 'boolean',
        )
        .slice(0, 5),
      routes: scene.routes
        .filter(
          (route) =>
            record(route) &&
            typeof route.method === 'string' &&
            typeof route.route === 'string' &&
            ['requests', 'errors', 'p95Ms'].every((key) => finite(route[key])),
        )
        .slice(0, 5),
    } as unknown as AlertScene
  } catch {
    return
  }
}
