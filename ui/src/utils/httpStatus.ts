export type HttpStatusTone = 'success' | 'redirect' | 'client-error' | 'server-error' | 'unknown'

export function httpStatusTone(code: string): HttpStatusTone {
  if (code.startsWith('2')) return 'success'
  if (code.startsWith('3')) return 'redirect'
  if (code.startsWith('4')) return 'client-error'
  if (code.startsWith('5') || code === '0') return 'server-error'
  return 'unknown'
}
