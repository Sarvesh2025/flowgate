package com.flowgate.gateway.controlplane;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RuleCache {

    private static final String KEY = "rules";

    private final RateLimitRuleRepository rules;
    private final RouteRepository routes;
    private final String defaultPath;
    private final TransactionTemplate tx;
    private final Cache<String, List<CachedRule>> cache = Caffeine.newBuilder().build();

    public RuleCache(
            RateLimitRuleRepository rules,
            RouteRepository routes,
            PlatformTransactionManager transactions,
            @Value("${flowgate.ratelimit.path}") String defaultPath) {
        this.rules = rules;
        this.routes = routes;
        this.defaultPath = defaultPath;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setReadOnly(true);
    }

    public Optional<CachedRule> match(String uri) {
        return snapshot().stream()
                .filter(r -> r.path().equals(uri))
                .max(Comparator.comparingInt(CachedRule::priority));
    }

    public void reload() {
        cache.put(KEY, loadFromDb());
    }

    private List<CachedRule> snapshot() {
        return cache.get(KEY, k -> loadFromDb());
    }

    private List<CachedRule> loadFromDb() {
        return tx.execute(
                status -> {
                    Map<UUID, String> paths =
                            routes.findAll().stream()
                                    .collect(
                                            Collectors.toMap(
                                                    RouteEntity::getId, RouteEntity::getPathPattern));
                    return rules.findAll().stream()
                            .filter(RateLimitRuleEntity::isEnabled)
                            .map(
                                    e -> new CachedRule(
                                            e.getId(),
                                            e.getRouteId() == null
                                                    ? defaultPath
                                                    : paths.getOrDefault(e.getRouteId(), defaultPath),
                                            e.getAlgorithm(),
                                            e.getLimitCount(),
                                            e.getWindowMs(),
                                            e.getPriority()))
                            .toList();
                });
    }
}
