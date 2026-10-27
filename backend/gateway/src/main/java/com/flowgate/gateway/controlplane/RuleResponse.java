package com.flowgate.gateway.controlplane;

import java.time.Instant;
import java.util.UUID;

public record RuleResponse(
        UUID id,
        String name,
        UUID routeId,
        String scope,
        String algorithm,
        int limitCount,
        int windowMs,
        int priority,
        String redisDownPolicy,
        Integer quotaLimit,
        String quotaPeriod,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    static RuleResponse from(RateLimitRuleEntity e) {
        return new RuleResponse(
                e.getId(),
                e.getName(),
                e.getRouteId(),
                e.getScope(),
                e.getAlgorithm(),
                e.getLimitCount(),
                e.getWindowMs(),
                e.getPriority(),
                e.getRedisDownPolicy(),
                e.getQuotaLimit(),
                e.getQuotaPeriod(),
                e.isEnabled(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
