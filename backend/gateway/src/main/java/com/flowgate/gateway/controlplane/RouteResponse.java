package com.flowgate.gateway.controlplane;

import java.time.Instant;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        String routeKey,
        String pathPattern,
        String methods,
        String rewritePrefix,
        String lbAlgorithm,
        int canaryPercent,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    static RouteResponse from(RouteEntity e) {
        return new RouteResponse(
                e.getId(),
                e.getRouteKey(),
                e.getPathPattern(),
                e.getMethods(),
                e.getRewritePrefix(),
                e.getLbAlgorithm(),
                e.getCanaryPercent(),
                e.isEnabled(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
