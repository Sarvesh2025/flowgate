package com.flowgate.gateway.proxy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProxyFilter extends OncePerRequestFilter {

    private final RouteCache routes;
    private final ProxyService proxy;

    public ProxyFilter(RouteCache routes, ProxyService proxy) {
        this.routes = routes;
        this.proxy = proxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/") || path.startsWith("/actuator") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CachedRoute> matched = routes.match(request.getMethod(), request.getRequestURI());
        if (matched.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        CachedRoute route = matched.get();
        byte[] inbound = request.getInputStream().readAllBytes();
        try {
            ProxyResult result = proxy.forward(route, request, inbound);
            response.setStatus(result.status());
            response.setHeader("X-Flowgate-Route", route.routeKey());
            response.setHeader("X-Flowgate-Cache", result.cacheHit() ? "HIT" : "MISS");
            if (result.upstream() != null && !result.upstream().isBlank()) {
                response.setHeader("X-Flowgate-Upstream", result.upstream());
            }
            response.setHeader("X-Flowgate-Canary", Boolean.toString(result.canary()));
            result.headers()
                    .forEach(
                            (name, values) -> {
                                if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)) {
                                    values.forEach(value -> response.addHeader(name, value));
                                }
                            });
            if (result.body() != null && result.body().length > 0) {
                response.getOutputStream().write(result.body());
            }
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }
}
