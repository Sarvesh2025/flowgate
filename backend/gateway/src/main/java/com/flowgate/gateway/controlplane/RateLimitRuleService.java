package com.flowgate.gateway.controlplane;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RateLimitRuleService {

    private static final Set<String> ALGORITHMS = Set.of(
            "TOKEN_BUCKET",
            "SLIDING_WINDOW_COUNTER",
            "SLIDING_WINDOW_LOG",
            "FIXED_WINDOW");

    private final RateLimitRuleRepository rules;
    private final RouteRepository routes;
    private final RuleCache ruleCache;
    private final ConfigChangePublisher publisher;
    private final AuditService audit;

    public RateLimitRuleService(
            RateLimitRuleRepository rules,
            RouteRepository routes,
            RuleCache ruleCache,
            ConfigChangePublisher publisher,
            AuditService audit) {
        this.rules = rules;
        this.routes = routes;
        this.ruleCache = ruleCache;
        this.publisher = publisher;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list() {
        return rules.findAll().stream().map(RuleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse get(UUID id) {
        return RuleResponse.from(find(id));
    }

    @Transactional
    public RuleResponse create(RuleRequest request) {
        RateLimitRuleEntity entity = new RateLimitRuleEntity();
        apply(entity, request, true);
        RuleResponse after = RuleResponse.from(rules.save(entity));
        audit.record("CREATE", after.id().toString(), null, after);
        notifyChanged();
        return after;
    }

    @Transactional
    public RuleResponse update(UUID id, RuleRequest request) {
        RateLimitRuleEntity entity = find(id);
        RuleResponse before = RuleResponse.from(entity);
        apply(entity, request, false);
        RuleResponse after = RuleResponse.from(rules.save(entity));
        audit.record("UPDATE", id.toString(), before, after);
        notifyChanged();
        return after;
    }

    @Transactional
    public void delete(UUID id) {
        RateLimitRuleEntity entity = find(id);
        RuleResponse before = RuleResponse.from(entity);
        rules.delete(entity);
        audit.record("DELETE", id.toString(), before, null);
        notifyChanged();
    }

    private RateLimitRuleEntity find(UUID id) {
        return rules.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "rule not found"));
    }

    private void notifyChanged() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        ruleCache.reload();
                        publisher.rulesChanged();
                    }
                });
    }

    private void apply(RateLimitRuleEntity entity, RuleRequest request, boolean creating) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (request.algorithm() == null || !ALGORITHMS.contains(request.algorithm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown algorithm");
        }
        if (request.limitCount() == null || request.limitCount() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limitCount must be >= 1");
        }
        if (request.windowMs() == null || request.windowMs() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowMs must be >= 1");
        }
        if (request.routeId() != null && !routes.existsById(request.routeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "route not found");
        }
        entity.setName(request.name());
        entity.setRouteId(request.routeId());
        entity.setScope(request.scope() != null ? request.scope() : "ROUTE");
        entity.setAlgorithm(request.algorithm());
        entity.setLimitCount(request.limitCount());
        entity.setWindowMs(request.windowMs());
        entity.setPriority(request.priority() != null ? request.priority() : 0);
        entity.setRedisDownPolicy(
                request.redisDownPolicy() != null ? request.redisDownPolicy() : "FAIL_CLOSED");
        entity.setQuotaLimit(request.quotaLimit());
        entity.setQuotaPeriod(request.quotaPeriod());
        if (creating) {
            entity.setEnabled(request.enabled() == null || request.enabled());
        } else if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
    }
}
