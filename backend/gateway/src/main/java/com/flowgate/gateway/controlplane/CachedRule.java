package com.flowgate.gateway.controlplane;

import java.util.UUID;

public record CachedRule(
        UUID id, String path, String algorithm, int limitCount, int windowMs, int priority) {}
