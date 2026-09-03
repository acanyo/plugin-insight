import { onBeforeUnmount, onMounted, ref } from 'vue'

export function usePolling(task: () => Promise<unknown>, intervalMs = 30_000) {
  const lastRunAt = ref(0)
  let timer: number | undefined

  async function run() {
    await task()
    lastRunAt.value = Date.now()
  }

  function handleVisibilityChange() {
    if (document.visibilityState !== 'visible') return
    if (Date.now() - lastRunAt.value >= intervalMs) void run()
  }

  onMounted(() => {
    timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void run()
    }, intervalMs)
    document.addEventListener('visibilitychange', handleVisibilityChange)
  })

  onBeforeUnmount(() => {
    if (timer !== undefined) window.clearInterval(timer)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  })

  return { lastRunAt, run }
}
