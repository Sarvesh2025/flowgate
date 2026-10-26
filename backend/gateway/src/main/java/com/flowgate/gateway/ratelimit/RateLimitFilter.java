package com.flowgate.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConfiguredRateLimiter limiter;
    private final String path;
    private final int capacity;
    private final long windowMs;

    public RateLimitFilter(
            ConfiguredRateLimiter limiter,
            @Value("${flowgate.ratelimit.path}") String path,
            @Value("${flowgate.ratelimit.capacity}") int capacity,
            @Value("${flowgate.ratelimit.window-ms}") long windowMs) {
        this.limiter = limiter;
        this.path = path;
        this.capacity = capacity;
        this.windowMs = windowMs;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!path.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        Decision decision = limiter.allow("global", capacity, windowMs);
        response.setHeader("X-RateLimit-Limit", Integer.toString(capacity));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", "1");
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests");
            return;
        }
        chain.doFilter(request, response);
    }
}
