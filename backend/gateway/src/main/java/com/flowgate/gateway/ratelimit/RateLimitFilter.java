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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConfiguredRateLimiter limiter;
    private final RuleCache ruleCache;
    private final String path;
    private final int capacity;
    private final long windowMs;

    public RateLimitFilter(
            ConfiguredRateLimiter limiter,
            RuleCache ruleCache,
            @Value("${flowgate.ratelimit.path}") String path,
            @Value("${flowgate.ratelimit.capacity}") int capacity,
            @Value("${flowgate.ratelimit.window-ms}") long windowMs) {
        this.limiter = limiter;
        this.ruleCache = ruleCache;
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
        Decision decision;
        int limit;
        if (matched.isPresent()) {
            CachedRule rule = matched.get();
            limit = rule.limitCount();
            decision =
                    limiter.allow(
                            rule.algorithm(),
                            "rule:" + rule.id(),
                            rule.limitCount(),
                            rule.windowMs());
        } else {
            limit = capacity;
            decision = limiter.allow("global", capacity, windowMs);
        }
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", "1");
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests");
            return;
        }
        chain.doFilter(request, response);
    }
}
