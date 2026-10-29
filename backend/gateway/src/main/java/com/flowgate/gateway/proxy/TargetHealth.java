package com.flowgate.gateway.proxy;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TargetHealth {

    private final ConcurrentHashMap<UUID, Boolean> overlay = new ConcurrentHashMap<>();

    public boolean isHealthy(CachedTarget target) {
        return overlay.getOrDefault(target.id(), target.seededHealthy());
    }

    public void mark(UUID id, boolean healthy) {
        overlay.put(id, healthy);
    }
}
