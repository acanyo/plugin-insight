package com.xhhao.insight.halo;

import java.time.Instant;

public record PluginRuntimeInfo(
    String name,
    String displayName,
    String version,
    String issuesUrl,
    boolean enabled,
    String phase,
    Instant lastStartTime
) {}
