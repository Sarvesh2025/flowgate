package com.flowgate.gateway.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class LoadBalancer {

    private final TargetHealth health;
    private final ConcurrentHashMap<UUID, AtomicInteger> rr = new ConcurrentHashMap<>();

    public LoadBalancer(TargetHealth health) {
        this.health = health;
    }

    public CachedTarget pick(CachedRoute route) {
        boolean wantCanary =
                route.canaryPercent() > 0
                        && ThreadLocalRandom.current().nextInt(100) < route.canaryPercent();
        List<CachedTarget> preferred = healthy(route, wantCanary);
        if (preferred.isEmpty()) {
            preferred = healthy(route, !wantCanary);
        }
        if (preferred.isEmpty()) {
            return null;
        }
        return switch (route.lbAlgorithm() == null ? "ROUND_ROBIN" : route.lbAlgorithm()) {
            case "WEIGHTED" -> weighted(preferred);
            case "RANDOM" -> preferred.get(ThreadLocalRandom.current().nextInt(preferred.size()));
            default -> roundRobin(route.id(), preferred);
        };
    }

    private List<CachedTarget> healthy(CachedRoute route, boolean canary) {
        List<CachedTarget> out = new ArrayList<>();
        for (CachedTarget t : route.targets()) {
            if (t.canary() == canary && health.isHealthy(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private CachedTarget roundRobin(UUID routeId, List<CachedTarget> pool) {
        int i = Math.floorMod(rr.computeIfAbsent(routeId, id -> new AtomicInteger()).getAndIncrement(), pool.size());
        return pool.get(i);
    }

    private static CachedTarget weighted(List<CachedTarget> pool) {
        int total = 0;
        for (CachedTarget t : pool) {
            total += Math.max(1, t.weight());
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (CachedTarget t : pool) {
            acc += Math.max(1, t.weight());
            if (pick < acc) {
                return t;
            }
        }
        return pool.getLast();
    }
}
