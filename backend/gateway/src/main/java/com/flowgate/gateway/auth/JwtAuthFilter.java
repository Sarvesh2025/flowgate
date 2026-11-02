package com.flowgate.gateway.auth;

import com.flowgate.gateway.observe.SpanSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.micrometer.tracing.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final SpanSupport spans;

    public JwtAuthFilter(JwtService jwt, SpanSupport spans) {
        this.jwt = jwt;
        this.spans = spans;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                        instanceof ApiKeyPrincipal) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            Span span = spans.start("auth");
            try (var scope = spans.scope(span)) {
                spans.tag("auth.method", "jwt");
                try {
                    Claims claims = jwt.parse(header.substring(7));
                    String role = claims.get("role", String.class);
                    var auth =
                            new UsernamePasswordAuthenticationToken(
                                    claims.getSubject(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    spans.tag("auth.outcome", "ok");
                    spans.tag("auth.role", role == null ? "" : role);
                } catch (JwtException ignored) {
                    SecurityContextHolder.clearContext();
                    spans.tag("auth.outcome", "invalid");
                }
            } finally {
                span.end();
            }
        }
        chain.doFilter(request, response);
    }
}
