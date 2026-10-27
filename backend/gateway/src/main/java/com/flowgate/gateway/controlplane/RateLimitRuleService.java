package com.flowgate.gateway.controlplane;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public RateLimitRuleService(RateLimitRuleRepository rules, RouteRepository routes) {
        this.rules = rules;
        this.routes = routes;
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
        return RuleResponse.from(rules.save(entity));
    }

    @Transactional
    public RuleResponse update(UUID id, RuleRequest request) {
        RateLimitRuleEntity entity = find(id);
        apply(entity, request, false);
        return RuleResponse.from(rules.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!rules.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "rule not found");
        }
        rules.deleteById(id);
    }

    private RateLimitRuleEntity find(UUID id) {
        return rules.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "rule not found"));
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
