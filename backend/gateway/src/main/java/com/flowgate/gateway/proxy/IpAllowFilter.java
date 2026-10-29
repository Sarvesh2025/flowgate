package com.flowgate.gateway.proxy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IpAllowFilter extends OncePerRequestFilter {

    private final ProxyProperties props;

    public IpAllowFilter(ProxyProperties props) {
        this.props = props;
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
        List<String> allow = props.getIpAllow();
        if (allow == null || allow.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        String ip = request.getRemoteAddr();
        if (!allow.contains(ip)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "ip not allowed");
            return;
        }
        chain.doFilter(request, response);
    }
}
