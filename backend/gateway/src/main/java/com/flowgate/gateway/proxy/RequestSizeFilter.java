package com.flowgate.gateway.proxy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeFilter extends OncePerRequestFilter {

    private final ProxyProperties props;

    public RequestSizeFilter(ProxyProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length > props.getMaxBodyBytes()) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "request body too large");
            return;
        }
        chain.doFilter(request, response);
    }
}
