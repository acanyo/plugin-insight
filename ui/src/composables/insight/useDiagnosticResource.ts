import { onBeforeUnmount, onMounted } from 'vue'
import { diagnosticGet } from '@/api/diagnostics'
import { createLatestRequestChannel } from '@/utils/latestRequest'
import { createResource, loadLatestResource } from './resourceState'
import { usePolling } from '../usePolling'

export function useDiagnosticResource<T>(
  path: string,
  poll = true,
  params?: () => Record<string, string | number>,
) {
  const state = createResource<T>()
  const channel = createLatestRequestChannel()
  const refresh = (silent = false) =>
    loadLatestResource(
      state,
      channel,
      (signal) => diagnosticGet<T>(path, signal, params?.()),
      silent,
    )
  onMounted(() => void refresh())
  onBeforeUnmount(channel.abort)
  if (poll) usePolling(() => refresh(true), 15000)
  return { ...state, refresh }
}
