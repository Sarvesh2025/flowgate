package com.flowgate.gateway.proxy;

import java.util.List;
import java.util.UUID;

public record CachedRoute(
        UUID id,
        String routeKey,
        String pathPattern,
        String methods,
        String rewritePrefix,
        String lbAlgorithm,
        int canaryPercent,
        boolean enabled,
        List<CachedTarget> targets) {}
