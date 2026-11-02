package com.flowgate.gateway.observe;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void rejected(String path) {
        registry.counter("flowgate.ratelimit.rejected", "path", path).increment();
    }

    public void proxied(String route, int status, boolean cacheHit, boolean canary) {
        String outcome = status >= 500 ? "error" : status >= 400 ? "client" : "ok";
        registry.counter(
                        "flowgate.proxy.requests",
                        "route",
                        route,
                        "outcome",
                        outcome,
                        "cache",
                        cacheHit ? "hit" : "miss",
                        "canary",
                        Boolean.toString(canary))
                .increment();
    }

    public double rejectedCount() {
        return registry.find("flowgate.ratelimit.rejected").counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    public double proxyCount() {
        return registry.find("flowgate.proxy.requests").counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
