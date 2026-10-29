package com.flowgate.gateway.proxy;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Component
public class ProxyService {

    private static final Set<String> HOP =
            Set.of(
                    "connection",
                    "keep-alive",
                    "proxy-authenticate",
                    "proxy-authorization",
                    "te",
                    "trailers",
                    "transfer-encoding",
                    "upgrade",
                    "host",
                    "content-length",
                    "cookie");

    private final WebClient webClient;
    private final LoadBalancer loadBalancer;
    private final TargetHealth health;
    private final RouteCache routes;
    private final StringRedisTemplate redis;
    private final ProxyProperties props;
    private final CircuitBreakerRegistry breakers;
    private final RetryRegistry retries;
    private final BulkheadRegistry bulkheads;
    private final TimeLimiterRegistry timers;

    public ProxyService(
            WebClient proxyWebClient,
            LoadBalancer loadBalancer,
            TargetHealth health,
            RouteCache routes,
            StringRedisTemplate redis,
            ProxyProperties props,
            CircuitBreakerRegistry breakers,
            RetryRegistry retries,
            BulkheadRegistry bulkheads,
            TimeLimiterRegistry timers) {
        this.webClient = proxyWebClient;
        this.loadBalancer = loadBalancer;
        this.health = health;
        this.routes = routes;
        this.redis = redis;
        this.props = props;
        this.breakers = breakers;
        this.retries = retries;
        this.bulkheads = bulkheads;
        this.timers = timers;
    }

    public ProxyResult forward(CachedRoute route, HttpServletRequest request, byte[] inbound)
            throws Exception {
        if ("GET".equalsIgnoreCase(request.getMethod()) && props.getCacheTtlSeconds() > 0) {
            String cached = redis.opsForValue().get(cacheKey(request));
            if (cached != null) {
                return decodeCache(cached);
            }
        }

        CachedTarget target = loadBalancer.pick(route);
        if (target == null) {
            return ProxyResult.fallback(503, "{\"error\":\"no_healthy_upstream\",\"route\":\"" + route.routeKey() + "\"}");
        }

        CircuitBreaker cb = breakers.circuitBreaker(route.routeKey());
        Retry retry = retries.retry(route.routeKey());
        Bulkhead bulkhead = bulkheads.bulkhead(route.routeKey());
        TimeLimiter limiter = timers.timeLimiter(route.routeKey());

        Supplier<ProxyResult> call = () -> fetch(route, target, request, inbound);
        Supplier<ProxyResult> decorated =
                Decorators.ofSupplier(call)
                        .withRetry(retry)
                        .withCircuitBreaker(cb)
                        .withBulkhead(bulkhead)
                        .withFallback(
                                List.of(
                                        CallNotPermittedException.class,
                                        BulkheadFullException.class,
                                        UpstreamFailure.class,
                                        WebClientRequestException.class),
                                ex -> fallback(route, ex))
                        .decorate();

        Callable<ProxyResult> timed =
                TimeLimiter.decorateFutureSupplier(
                        limiter, () -> CompletableFuture.supplyAsync(decorated));
        try {
            ProxyResult result = timed.call();
            if (!result.cacheHit()
                    && result.status() == 200
                    && "GET".equalsIgnoreCase(request.getMethod())
                    && props.getCacheTtlSeconds() > 0
                    && result.body() != null) {
                redis.opsForValue()
                        .set(
                                cacheKey(request),
                                encodeCache(result),
                                Duration.ofSeconds(props.getCacheTtlSeconds()));
            }
            return result;
        } catch (TimeoutException ex) {
            return ProxyResult.fallback(504, "{\"error\":\"upstream_timeout\",\"route\":\"" + route.routeKey() + "\"}");
        }
    }

    private ProxyResult fetch(
            CachedRoute route, CachedTarget target, HttpServletRequest request, byte[] inbound) {
        String path = routes.rewrite(route, request.getRequestURI());
        String base = trimSlash(target.url());
        String uri = base + path;
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            uri = uri + "?" + request.getQueryString();
        }
        byte[] body = inbound == null ? new byte[0] : inbound;
        ResponseEntity<byte[]> entity =
                webClient
                        .method(HttpMethod.valueOf(request.getMethod()))
                        .uri(uri)
                        .headers(headers -> copyRequestHeaders(request, headers, route.routeKey()))
                        .body(body.length == 0 ? BodyInserters.empty() : BodyInserters.fromValue(body))
                        .retrieve()
                        .onStatus(status -> false, response -> reactor.core.publisher.Mono.empty())
                        .toEntity(byte[].class)
                        .block(Duration.ofMillis(props.getReadTimeoutMs()));
        if (entity == null) {
            health.mark(target.id(), false);
            throw new UpstreamFailure(502, "empty upstream response");
        }
        int status = entity.getStatusCode().value();
        if (entity.getStatusCode().is5xxServerError()) {
            health.mark(target.id(), false);
            throw new UpstreamFailure(status, "upstream " + status);
        }
        health.mark(target.id(), true);
        HttpHeaders out = new HttpHeaders();
        entity.getHeaders()
                .forEach(
                        (name, values) -> {
                            if (!HOP.contains(name.toLowerCase())) {
                                out.put(name, values);
                            }
                        });
        byte[] payload = entity.getBody() == null ? new byte[0] : entity.getBody();
        return new ProxyResult(status, out, payload, target.url(), false, target.canary());
    }

    private static ProxyResult fallback(CachedRoute route, Throwable ex) {
        int status = 503;
        String kind = "upstream_unavailable";
        if (ex instanceof CallNotPermittedException) {
            kind = "circuit_open";
        } else if (ex instanceof BulkheadFullException) {
            kind = "bulkhead_full";
        } else if (ex instanceof UpstreamFailure fail) {
            status = fail.status() >= 500 ? 502 : fail.status();
            kind = "upstream_error";
        } else if (ex instanceof WebClientRequestException) {
            status = 502;
            kind = "upstream_connect";
        }
        return ProxyResult.fallback(
                status, "{\"error\":\"" + kind + "\",\"route\":\"" + route.routeKey() + "\"}");
    }

    private static void copyRequestHeaders(
            HttpServletRequest request, HttpHeaders headers, String routeKey) {
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (HOP.contains(name.toLowerCase())) {
                continue;
            }
            headers.add(name, request.getHeader(name));
        }
        headers.set("X-Flowgate-Route", routeKey);
        Object cid = request.getAttribute("correlationId");
        if (cid != null) {
            headers.set("X-Correlation-Id", cid.toString());
        }
        String forwarded = request.getRemoteAddr();
        String existing = request.getHeader("X-Forwarded-For");
        headers.set("X-Forwarded-For", existing == null ? forwarded : existing + ", " + forwarded);
    }

    private static String cacheKey(HttpServletRequest request) {
        String q = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        return "proxy:cache:" + request.getMethod() + ":" + request.getRequestURI() + q;
    }

    private static String encodeCache(ProxyResult result) {
        String type = result.headers().getFirst(HttpHeaders.CONTENT_TYPE);
        if (type == null) {
            type = "application/octet-stream";
        }
        return result.status()
                + "|"
                + type
                + "|"
                + (result.upstream() == null ? "" : result.upstream())
                + "|"
                + Base64.getEncoder().encodeToString(result.body());
    }

    private static ProxyResult decodeCache(String raw) {
        String[] parts = raw.split("\\|", 4);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, parts[1]);
        byte[] body = parts.length < 4 ? new byte[0] : Base64.getDecoder().decode(parts[3]);
        return new ProxyResult(Integer.parseInt(parts[0]), headers, body, parts[2], true, false);
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
