package com.xhhao.insight.halo;

import java.util.List;

public record HaloRuntimeSnapshot(
    String haloVersion,
    String activatedTheme,
    List<PluginRuntimeInfo> plugins,
    List<DependencyStatus> dependencies
) {}
