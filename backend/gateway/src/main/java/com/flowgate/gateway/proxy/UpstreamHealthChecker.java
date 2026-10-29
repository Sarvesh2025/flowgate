package com.flowgate.gateway.proxy;

import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class UpstreamHealthChecker {

    private final RouteCache routes;
    private final TargetHealth health;
    private final WebClient webClient;

    public UpstreamHealthChecker(RouteCache routes, TargetHealth health, WebClient proxyWebClient) {
        this.routes = routes;
        this.health = health;
        this.webClient = proxyWebClient;
    }

    @Scheduled(fixedDelayString = "${flowgate.proxy.health-interval-ms:10000}")
    public void probe() {
        for (CachedRoute route : routes.snapshot()) {
            for (CachedTarget target : route.targets()) {
                String url = target.url().endsWith("/")
                        ? target.url().substring(0, target.url().length() - 1)
                        : target.url();
                try {
                    webClient
                            .get()
                            .uri(url + "/health")
                            .retrieve()
                            .toBodilessEntity()
                            .block(Duration.ofSeconds(1));
                    health.mark(target.id(), true);
                } catch (Exception ex) {
                    health.mark(target.id(), false);
                }
            }
        }
    }
}
