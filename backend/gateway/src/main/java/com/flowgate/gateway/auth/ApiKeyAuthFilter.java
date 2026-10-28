package com.flowgate.gateway.auth;

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

    public ApiKeyAuthFilter(ApiKeyService keys) {
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getHeader("X-API-Key");
        if (raw != null && !raw.isBlank()) {
            try {
                ApiKeyPrincipal principal = keys.authenticate(raw);
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_API")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ResponseStatusException ex) {
                response.sendError(ex.getStatusCode().value(), ex.getReason());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
