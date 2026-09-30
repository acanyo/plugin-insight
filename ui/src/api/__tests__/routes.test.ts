import { axiosInstance } from '@halo-dev/api-client'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchRoutes } from '@/api/insight'

vi.mock('@halo-dev/api-client', () => ({
  axiosInstance: {
    get: vi.fn<(url: string, config?: unknown) => Promise<{ data: unknown }>>(),
  },
}))

beforeEach(() => {
  vi.resetAllMocks()
})

describe('fetchRoutes', () => {
  it('passes the selected ranking to the routes endpoint', async () => {
    vi.mocked(axiosInstance.get).mockResolvedValue({ data: [] })
    const controller = new AbortController()

    await fetchRoutes('6h', 'error-rate', controller.signal)

    expect(axiosInstance.get).toHaveBeenCalledWith(
      '/apis/console.api.insight.halo.run/v1alpha1/routes',
      {
        params: { range: '6h', sort: 'error-rate' },
        signal: controller.signal,
        timeout: 15_000,
      },
    )
  })
})
