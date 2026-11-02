package com.flowgate.gateway.ratelimit;

import com.flowgate.gateway.controlplane.CachedRule;
import com.flowgate.gateway.controlplane.RuleCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.flowgate.gateway.auth.ApiKeyPrincipal;
import com.flowgate.gateway.observe.GatewayMetrics;
import com.flowgate.gateway.observe.SpanSupport;
import io.micrometer.tracing.Span;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConfiguredRateLimiter limiter;
    private final RuleCache ruleCache;
    private final GatewayMetrics metrics;
    private final SpanSupport spans;
    private final String path;
    private final int capacity;
    private final long windowMs;

    public RateLimitFilter(
            ConfiguredRateLimiter limiter,
            RuleCache ruleCache,
            GatewayMetrics metrics,
            SpanSupport spans,
            @Value("${flowgate.ratelimit.path}") String path,
            @Value("${flowgate.ratelimit.capacity}") int capacity,
            @Value("${flowgate.ratelimit.window-ms}") long windowMs) {
        this.limiter = limiter;
        this.ruleCache = ruleCache;
        this.metrics = metrics;
        this.spans = spans;
        this.path = path;
        this.capacity = capacity;
        this.windowMs = windowMs;
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
            Decision decision;
            int limit;
            String bucket = limiterKey();
            if (matched.isPresent()) {
                CachedRule rule = matched.get();
                limit = rule.limitCount();
                spans.tag("ratelimit.algorithm", rule.algorithm());
                decision =
                        limiter.allow(
                                rule.algorithm(),
                                "rule:" + rule.id() + ":" + bucket,
                                rule.limitCount(),
                                rule.windowMs());
            } else {
                limit = capacity;
                spans.tag("ratelimit.algorithm", "yaml");
                decision = limiter.allow(bucket, capacity, windowMs);
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
