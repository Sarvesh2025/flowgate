package com.flowgate.gateway.controlplane;

import java.util.UUID;

public record RuleRequest(
        String name,
        UUID routeId,
        String scope,
        String algorithm,
        Integer limitCount,
        Integer windowMs,
        Integer priority,
        String redisDownPolicy,
        Integer quotaLimit,
        String quotaPeriod,
        Boolean enabled) {}
