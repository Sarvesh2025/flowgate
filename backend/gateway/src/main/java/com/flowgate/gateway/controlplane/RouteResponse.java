package com.flowgate.gateway.controlplane;

import java.time.Instant;
import java.util.List;
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
        Instant updatedAt,
        List<TargetView> targets) {

    public record TargetView(UUID id, String url, int weight, boolean canary, boolean healthy) {}

    static RouteResponse from(RouteEntity e, List<UpstreamTargetEntity> targets) {
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
                e.getUpdatedAt(),
                targets.stream()
                        .map(
                                t ->
                                        new TargetView(
                                                t.getId(),
                                                t.getUrl(),
                                                t.getWeight(),
                                                t.isCanary(),
                                                t.isHealthy()))
                        .toList());
    }
}
