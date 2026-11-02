package com.flowgate.gateway.controlplane;

import com.flowgate.gateway.proxy.RouteCache;
import com.flowgate.gateway.proxy.TargetHealth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    public record RouteWrite(
            String routeKey,
            String pathPattern,
            String methods,
            String rewritePrefix,
            String lbAlgorithm,
            Integer canaryPercent,
            Boolean enabled) {}

    public record RoutePatch(Boolean enabled, Integer canaryPercent, String lbAlgorithm) {}

    public record TargetWrite(String url, Integer weight, Boolean canary) {}

    private static final Set<String> LB = Set.of("ROUND_ROBIN", "WEIGHTED", "RANDOM");

    private final RouteRepository routes;
    private final UpstreamTargetRepository targets;
    private final TargetHealth health;
    private final RouteCache routeCache;
    private final ConfigChangePublisher publisher;
    private final AuditService audit;

    public RouteController(
            RouteRepository routes,
            UpstreamTargetRepository targets,
            TargetHealth health,
            RouteCache routeCache,
            ConfigChangePublisher publisher,
            AuditService audit) {
        this.routes = routes;
        this.targets = targets;
        this.health = health;
        this.routeCache = routeCache;
        this.publisher = publisher;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RouteResponse> list() {
        return mapped();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RouteResponse create(@RequestBody RouteWrite body) {
        if (body.routeKey() == null || body.routeKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "routeKey is required");
        }
        if (routes.existsByRouteKey(body.routeKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "routeKey exists");
        }
        RouteEntity entity = new RouteEntity();
        apply(entity, body, true);
        RouteResponse after = respond(routes.save(entity));
        audit.record("CREATE", "route", after.id().toString(), null, after);
        notifyChanged();
        return after;
    }

    @PutMapping("/{id}")
    @Transactional
    public RouteResponse update(@PathVariable UUID id, @RequestBody RouteWrite body) {
        RouteEntity entity = findRoute(id);
        RouteResponse before = respond(entity);
        apply(entity, body, false);
        RouteResponse after = respond(routes.save(entity));
        audit.record("UPDATE", "route", id.toString(), before, after);
        notifyChanged();
        return after;
    }

    @PatchMapping("/{id}")
    @Transactional
    public RouteResponse patch(@PathVariable UUID id, @RequestBody RoutePatch body) {
        RouteEntity entity = findRoute(id);
        RouteResponse before = respond(entity);
        if (body.enabled() != null) {
            entity.setEnabled(body.enabled());
        }
        if (body.canaryPercent() != null) {
            setCanary(entity, body.canaryPercent());
        }
        if (body.lbAlgorithm() != null) {
            entity.setLbAlgorithm(requireLb(body.lbAlgorithm()));
        }
        RouteResponse after = respond(routes.save(entity));
        audit.record("UPDATE", "route", id.toString(), before, after);
        notifyChanged();
        return after;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        RouteEntity entity = findRoute(id);
        RouteResponse before = respond(entity);
        routes.delete(entity);
        audit.record("DELETE", "route", id.toString(), before, null);
        notifyChanged();
    }

    @PostMapping("/{id}/targets")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RouteResponse addTarget(@PathVariable UUID id, @RequestBody TargetWrite body) {
        findRoute(id);
        UpstreamTargetEntity target = new UpstreamTargetEntity();
        target.setRouteId(id);
        applyTarget(target, body);
        targets.save(target);
        RouteResponse after = respond(findRoute(id));
        audit.record("CREATE", "upstream_target", target.getId().toString(), null, after);
        notifyChanged();
        return after;
    }

    @DeleteMapping("/{id}/targets/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteTarget(@PathVariable UUID id, @PathVariable UUID targetId) {
        UpstreamTargetEntity target =
                targets.findById(targetId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "target not found"));
        if (!id.equals(target.getRouteId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "target not found");
        }
        targets.delete(target);
        audit.record("DELETE", "upstream_target", targetId.toString(), target.getUrl(), null);
        notifyChanged();
    }

    private void apply(RouteEntity entity, RouteWrite body, boolean creating) {
        if (body.pathPattern() == null || body.pathPattern().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pathPattern is required");
        }
        if (creating || body.routeKey() != null) {
            entity.setRouteKey(body.routeKey());
        }
        entity.setPathPattern(body.pathPattern());
        entity.setMethods(body.methods() == null || body.methods().isBlank() ? "GET" : body.methods());
        entity.setRewritePrefix(body.rewritePrefix());
        entity.setLbAlgorithm(requireLb(body.lbAlgorithm() == null ? "ROUND_ROBIN" : body.lbAlgorithm()));
        setCanary(entity, body.canaryPercent() == null ? 0 : body.canaryPercent());
        if (body.enabled() != null) {
            entity.setEnabled(body.enabled());
        } else if (creating) {
            entity.setEnabled(true);
        }
    }

    private static void applyTarget(UpstreamTargetEntity target, TargetWrite body) {
        if (body.url() == null || body.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }
        target.setUrl(body.url().replaceAll("/$", ""));
        target.setWeight(body.weight() == null || body.weight() < 1 ? 1 : body.weight());
        target.setCanary(Boolean.TRUE.equals(body.canary()));
    }

    private void setCanary(RouteEntity entity, int percent) {
        if (percent < 0 || percent > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "canaryPercent 0-100");
        }
        entity.setCanaryPercent(percent);
    }

    private static String requireLb(String algorithm) {
        if (!LB.contains(algorithm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown lbAlgorithm");
        }
        return algorithm;
    }

    private RouteEntity findRoute(UUID id) {
        return routes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "route not found"));
    }

    private RouteResponse respond(RouteEntity entity) {
        Map<UUID, List<UpstreamTargetEntity>> byRoute = grouped();
        return RouteResponse.from(entity, byRoute.getOrDefault(entity.getId(), List.of()), health::live);
    }

    private List<RouteResponse> mapped() {
        Map<UUID, List<UpstreamTargetEntity>> byRoute = grouped();
        return routes.findAll().stream()
                .map(r -> RouteResponse.from(r, byRoute.getOrDefault(r.getId(), List.of()), health::live))
                .toList();
    }

    private Map<UUID, List<UpstreamTargetEntity>> grouped() {
        return targets.findAll().stream().collect(Collectors.groupingBy(UpstreamTargetEntity::getRouteId));
    }

    private void notifyChanged() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        routeCache.reload();
                        publisher.rulesChanged();
                    }
                });
    }
}
