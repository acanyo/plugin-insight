package com.xhhao.insight.halo;

import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.SpringPluginManager;

/** Root context access for read-only host diagnostics. */
@Component
public class HaloHostContext {
    private final ApplicationContext root;
    public HaloHostContext(PluginWrapper plugin) {
        root = plugin.getPluginManager() instanceof SpringPluginManager manager ? manager.getRootContext() : null;
    }
    public ApplicationContext root() { return root; }
}
