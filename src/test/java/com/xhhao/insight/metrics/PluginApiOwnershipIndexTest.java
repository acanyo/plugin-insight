package com.xhhao.insight.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationContext;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.plugin.SpringPlugin;
import run.halo.app.plugin.SpringPluginManager;

class PluginApiOwnershipIndexTest {

    @Test
    void shouldResolveFunctionalEndpointGroupToCanonicalPluginId() {
        AtomicLong clock = new AtomicLong();
        SpringPluginManager manager = mock(SpringPluginManager.class);
        PluginWrapper appStore = plugin(
            "app-store-integration", "console.api.appstore.halo.run");
        when(manager.startedPlugins()).thenReturn(List.of(appStore));

        PluginApiOwnershipIndex index = new PluginApiOwnershipIndex(manager, clock::get);

        assertThat(index.pluginId("CONSOLE.API.APPSTORE.HALO.RUN/V1ALPHA1"))
            .contains("app-store-integration");
    }

    @Test
    void shouldNotAttributeAnApiGroupClaimedByTwoPlugins() {
        SpringPluginManager manager = mock(SpringPluginManager.class);
        List<PluginWrapper> plugins = List.of(
            plugin("first", "shared.example.run"),
            plugin("second", "shared.example.run")
        );
        when(manager.startedPlugins()).thenReturn(plugins);

        PluginApiOwnershipIndex index = new PluginApiOwnershipIndex(manager, () -> 0L);

        assertThat(index.pluginId("shared.example.run/v1alpha1")).isEmpty();
    }

    private static PluginWrapper plugin(String pluginId, String group) {
        CustomEndpoint endpoint = mock(CustomEndpoint.class);
        when(endpoint.groupVersion()).thenReturn(new GroupVersion(group, "v1alpha1"));
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansOfType(CustomEndpoint.class, false, false))
            .thenReturn(Map.of("endpoint", endpoint));
        org.pf4j.Plugin springPlugin = mock(
            org.pf4j.Plugin.class,
            withSettings().extraInterfaces(SpringPlugin.class)
        );
        when(((SpringPlugin) springPlugin).getApplicationContext()).thenReturn(context);
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginId()).thenReturn(pluginId);
        when(wrapper.getPlugin()).thenReturn(springPlugin);
        return wrapper;
    }
}
