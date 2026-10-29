package com.flowgate.gateway.proxy;

import com.flowgate.gateway.controlplane.RouteRepository;
import com.flowgate.gateway.controlplane.UpstreamTargetEntity;
import com.flowgate.gateway.controlplane.UpstreamTargetRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.AntPathMatcher;

@Component
public class RouteCache {

    private static final String KEY = "routes";

    private final RouteRepository routes;
    private final UpstreamTargetRepository targets;
    private final TransactionTemplate tx;
    private final AntPathMatcher paths = new AntPathMatcher();
    private final Cache<String, List<CachedRoute>> cache = Caffeine.newBuilder().build();

    public RouteCache(
            RouteRepository routes,
            UpstreamTargetRepository targets,
            PlatformTransactionManager transactions) {
        this.routes = routes;
        this.targets = targets;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setReadOnly(true);
    }

    public Optional<CachedRoute> match(String method, String uri) {
        String verb = method.toUpperCase(Locale.ROOT);
        return snapshot().stream()
                .filter(CachedRoute::enabled)
                .filter(r -> methodMatches(r.methods(), verb))
                .filter(r -> paths.match(r.pathPattern(), uri))
                .max(Comparator.comparingInt(r -> r.pathPattern().length()));
    }

    public List<CachedRoute> snapshot() {
        return cache.get(KEY, k -> loadFromDb());
    }

    public void reload() {
        cache.put(KEY, loadFromDb());
    }

    public String rewrite(CachedRoute route, String uri) {
        String prefix = route.rewritePrefix();
        if (prefix == null || prefix.isBlank()) {
            return uri;
        }
        String rest = paths.extractPathWithinPattern(route.pathPattern(), uri);
        if (rest == null || rest.isBlank()) {
            return prefix;
        }
        if (prefix.endsWith("/")) {
            return prefix + rest;
        }
        return prefix + "/" + rest;
    }

    private boolean methodMatches(String methods, String verb) {
        if (methods == null || methods.isBlank() || "*".equals(methods.trim())) {
            return true;
        }
        for (String part : methods.split(",")) {
            if (verb.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<CachedRoute> loadFromDb() {
        return tx.execute(
                status -> {
                    Map<UUID, List<UpstreamTargetEntity>> byRoute =
                            targets.findAll().stream()
                                    .collect(Collectors.groupingBy(UpstreamTargetEntity::getRouteId));
                    return routes.findAll().stream()
                            .map(
                                    r -> new CachedRoute(
                                            r.getId(),
                                            r.getRouteKey(),
                                            r.getPathPattern(),
                                            r.getMethods(),
                                            r.getRewritePrefix(),
                                            r.getLbAlgorithm(),
                                            r.getCanaryPercent(),
                                            r.isEnabled(),
                                            byRoute.getOrDefault(r.getId(), List.of()).stream()
                                                    .map(RouteCache::toTarget)
                                                    .toList()))
                            .toList();
                });
    }

    private static CachedTarget toTarget(UpstreamTargetEntity e) {
        return new CachedTarget(e.getId(), e.getUrl(), e.getWeight(), e.isCanary(), e.isHealthy());
    }
}
