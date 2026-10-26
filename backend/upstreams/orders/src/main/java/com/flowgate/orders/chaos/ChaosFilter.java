package com.flowgate.orders.chaos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChaosFilter extends OncePerRequestFilter {

    private final ChaosSettings settings;

    public ChaosFilter(ChaosSettings settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/chaos")) {
            chain.doFilter(request, response);
            return;
        }
        if (settings.isForceDown()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "chaos forceDown");
            return;
        }
        if (settings.getDelayMs() > 0) {
            try {
                Thread.sleep(settings.getDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (settings.getErrorRate() > 0
                && ThreadLocalRandom.current().nextDouble() < settings.getErrorRate()) {
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "chaos error");
            return;
        }
        chain.doFilter(request, response);
    }
}
