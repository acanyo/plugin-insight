import { consoleApiClient } from '@halo-dev/api-client'

export async function disablePlugin(pluginId: string): Promise<void> {
  return changePluginState(pluginId, false)
}

export async function enablePlugin(pluginId: string): Promise<void> {
  return changePluginState(pluginId, true)
}

async function changePluginState(pluginId: string, enable: boolean): Promise<void> {
  await consoleApiClient.plugin.plugin.changePluginRunningState(
    {
      name: pluginId,
      pluginRunningStateRequest: {
        enable,
        async: false,
      },
    },
    { timeout: 30_000 },
  )
}
