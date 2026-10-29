package com.flowgate.gateway.proxy;

import java.util.UUID;

public record CachedTarget(UUID id, String url, int weight, boolean canary, boolean seededHealthy) {}
