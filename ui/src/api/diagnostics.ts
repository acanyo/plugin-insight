import { axiosInstance } from '@halo-dev/api-client'
import type { Observation } from '@/types/diagnostics'

const BASE = '/apis/console.api.insight.halo.run/v1alpha1'
export async function diagnosticGet<T>(
  resource: string,
  signal?: AbortSignal,
  params?: Record<string, string | number>,
): Promise<T> {
  const { data } = await axiosInstance.get<T>(`${BASE}/${resource}`, {
    signal,
    params,
    timeout: 15000,
  })
  return data
}
export async function prepareObservation(pluginId: string): Promise<Observation> {
  const { data } = await axiosInstance.post<Observation>(`${BASE}/observations`, null, {
    params: { pluginId },
    timeout: 15000,
  })
  return data
}
export async function observationAction(
  id: string,
  action: 'confirm' | 'cancel' | 'restored',
): Promise<Observation> {
  const { data } = await axiosInstance.post<Observation>(
    `${BASE}/observations/${encodeURIComponent(id)}/${action}`,
    null,
    { timeout: 15000 },
  )
  return data
}
export async function startRecording(seconds: number) {
  await axiosInstance.post(`${BASE}/recordings`, null, { params: { seconds }, timeout: 15000 })
}
export async function downloadRecording(id: string) {
  const { data } = await axiosInstance.get<Blob>(
    `${BASE}/recordings/${encodeURIComponent(id)}/download`,
    { responseType: 'blob', timeout: 60000 },
  )
  const url = URL.createObjectURL(data)
  const link = document.createElement('a')
  link.href = url
  link.download = `insight-${id}.jfr`
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
