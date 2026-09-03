package com.xhhao.insight.halo;

import java.time.Instant;

public record PluginRuntimeInfo(
    String name,
    String displayName,
    String version,
    boolean enabled,
    String phase,
    Instant lastStartTime
) {}
