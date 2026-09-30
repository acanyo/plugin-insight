import { isAxiosError } from 'axios'
import { ref, shallowRef, type Ref, type ShallowRef } from 'vue'

import { errorMessage } from '@/utils/format'
import type { LatestRequestChannel, LatestRequestContext } from '@/utils/latestRequest'

export interface ResourceState<T> {
  data: ShallowRef<T | undefined>
  loading: Ref<boolean>
  error: Ref<string | undefined>
}

export function createResource<T>(): ResourceState<T> {
  return {
    data: shallowRef<T>(),
    loading: ref(false),
    error: ref<string>(),
  }
}

export function isCanceledRequest(error: unknown): boolean {
  return isAxiosError(error) && (error.code === 'ERR_CANCELED' || error.name === 'CanceledError')
}

export async function loadResource<T>(
  state: ResourceState<T>,
  request: LatestRequestContext,
  loader: (signal: AbortSignal) => Promise<T>,
  silent = false,
): Promise<boolean> {
  if (!silent || state.data.value === undefined) state.loading.value = true
  state.error.value = undefined

  try {
    const data = await loader(request.signal)
    if (!request.isCurrent()) return false
    state.data.value = data
    return true
  } catch (error) {
    if (request.isCurrent() && !isCanceledRequest(error)) {
      state.error.value = errorMessage(error)
    }
    return false
  } finally {
    if (request.isCurrent()) state.loading.value = false
  }
}

export function loadLatestResource<T>(
  state: ResourceState<T>,
  channel: LatestRequestChannel,
  loader: (signal: AbortSignal) => Promise<T>,
  silent = false,
): Promise<boolean> {
  return loadResource(state, channel.start(), loader, silent)
}
