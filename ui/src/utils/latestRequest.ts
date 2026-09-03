export interface LatestRequestContext {
  readonly signal: AbortSignal
  isCurrent: () => boolean
}

export interface LatestRequestChannel {
  start: () => LatestRequestContext
  abort: () => void
}

export function createLatestRequestChannel(): LatestRequestChannel {
  let generation = 0
  let controller: AbortController | undefined

  function abort() {
    generation += 1
    controller?.abort()
    controller = undefined
  }

  function start(): LatestRequestContext {
    abort()
    const requestGeneration = generation
    controller = new AbortController()
    const { signal } = controller

    return {
      signal,
      isCurrent: () => requestGeneration === generation && !signal.aborted,
    }
  }

  return { start, abort }
}
