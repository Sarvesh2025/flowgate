package com.flowgate.gateway.ratelimit;

import com.flowgate.gateway.auth.ApiKeyPrincipal;
import com.flowgate.gateway.controlplane.CachedRule;
import com.flowgate.gateway.controlplane.RuleCache;
import com.flowgate.gateway.observe.GatewayMetrics;
import com.flowgate.gateway.observe.SpanSupport;
import io.lettuce.core.RedisException;
import io.micrometer.tracing.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConfiguredRateLimiter limiter;
    private final LocalWindowLimiter local;
    private final RuleCache ruleCache;
    private final GatewayMetrics metrics;
    private final SpanSupport spans;
    private final String path;
    private final int capacity;
    private final long windowMs;
    private final String yamlPolicy;
    private final String instanceId;

    public RateLimitFilter(
            ConfiguredRateLimiter limiter,
            LocalWindowLimiter local,
            RuleCache ruleCache,
            GatewayMetrics metrics,
            SpanSupport spans,
            @Value("${flowgate.ratelimit.path}") String path,
            @Value("${flowgate.ratelimit.capacity}") int capacity,
            @Value("${flowgate.ratelimit.window-ms}") long windowMs,
            @Value("${flowgate.ratelimit.redis-down-policy:FAIL_CLOSED}") String yamlPolicy,
            @Value("${flowgate.instance-id:a}") String instanceId) {
        this.limiter = limiter;
        this.local = local;
        this.ruleCache = ruleCache;
        this.metrics = metrics;
        this.spans = spans;
        this.path = path;
        this.capacity = capacity;
        this.windowMs = windowMs;
        this.yamlPolicy = yamlPolicy;
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CachedRule> matched = ruleCache.match(request.getRequestURI());
        if (matched.isEmpty() && !path.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        Span span = spans.start("ratelimit");
        try (var scope = spans.scope(span)) {
            String algorithm;
            int limit;
            long window;
            String policy;
            String redisKey;
            String bucket = limiterKey();
            if (matched.isPresent()) {
                CachedRule rule = matched.get();
                algorithm = rule.algorithm();
                limit = rule.limitCount();
                window = rule.windowMs();
                policy = rule.redisDownPolicy() == null ? yamlPolicy : rule.redisDownPolicy();
                redisKey = "rule:" + rule.id() + ":" + bucket;
            } else {
                algorithm = null;
                limit = capacity;
                window = windowMs;
                policy = yamlPolicy;
                redisKey = bucket;
            }
            spans.tag("ratelimit.algorithm", algorithm == null ? "yaml" : algorithm);
            spans.tag("ratelimit.redis_down_policy", policy);
            response.setHeader("X-Flowgate-Instance", instanceId);
            Decision decision;
            try {
                decision =
                        algorithm == null
                                ? limiter.allow(redisKey, limit, window)
                                : limiter.allow(algorithm, redisKey, limit, window);
                response.setHeader("X-Flowgate-Redis", "UP");
            } catch (RuntimeException ex) {
                if (!redisDown(ex)) {
                    throw ex;
                }
                spans.tag("ratelimit.redis", "DOWN");
                if ("FAIL_OPEN".equalsIgnoreCase(policy)) {
                    decision = local.allow("local:" + redisKey, limit, window);
                    response.setHeader("X-Flowgate-Redis", "DOWN-OPEN");
                } else {
                    response.setHeader("X-Flowgate-Redis", "DOWN-CLOSED");
                    response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "redis unavailable");
                    return;
                }
            }
            spans.tag("ratelimit.allowed", Boolean.toString(decision.allowed()));
            spans.tag("ratelimit.remaining", Long.toString(decision.remaining()));
            response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
            if (!decision.allowed()) {
                metrics.rejected(request.getRequestURI());
                response.setHeader("Retry-After", "1");
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            span.end();
        }
    }

    private static boolean redisDown(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof RedisConnectionFailureException
                    || cursor instanceof RedisSystemException
                    || cursor instanceof RedisException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String limiterKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ApiKeyPrincipal key) {
            return "apikey:" + key.id();
        }
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String user) {
            return "user:" + user;
        }
        return "anon";
    }
}
