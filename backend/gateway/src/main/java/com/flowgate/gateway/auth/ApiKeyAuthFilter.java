package com.flowgate.gateway.auth;

import com.flowgate.gateway.observe.SpanSupport;
import io.micrometer.tracing.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService keys;
    private final SpanSupport spans;

    public ApiKeyAuthFilter(ApiKeyService keys, SpanSupport spans) {
        this.keys = keys;
        this.spans = spans;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getHeader("X-API-Key");
        if (raw != null && !raw.isBlank()) {
            Span span = spans.start("auth");
            try (var scope = spans.scope(span)) {
                spans.tag("auth.method", "apikey");
                try {
                    ApiKeyPrincipal principal = keys.authenticate(raw);
                    var auth =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_API")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    spans.tag("auth.outcome", "ok");
                } catch (ResponseStatusException ex) {
                    spans.tag("auth.outcome", "invalid");
                    span.error(ex);
                    response.sendError(ex.getStatusCode().value(), ex.getReason());
                    return;
                }
            } finally {
                span.end();
            }
        }
        chain.doFilter(request, response);
    }
}
