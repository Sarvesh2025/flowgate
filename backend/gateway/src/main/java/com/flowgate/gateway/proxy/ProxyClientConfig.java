package com.flowgate.gateway.proxy;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;

@Configuration
public class ProxyClientConfig {

    @Bean
    WebClient proxyWebClient(ProxyProperties props) {
        HttpClient httpClient =
                HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMs())
                        .responseTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config =
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .failureRateThreshold(50f)
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .recordExceptions(UpstreamFailure.class, WebClientRequestException.class)
                        .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    RetryRegistry retryRegistry() {
        RetryConfig config =
                RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(Duration.ofMillis(100))
                        .retryExceptions(UpstreamFailure.class, WebClientRequestException.class)
                        .build();
        return RetryRegistry.of(config);
    }

    @Bean
    BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig config =
                BulkheadConfig.custom()
                        .maxConcurrentCalls(20)
                        .maxWaitDuration(Duration.ofMillis(100))
                        .build();
        return BulkheadRegistry.of(config);
    }

    @Bean
    TimeLimiterRegistry timeLimiterRegistry(ProxyProperties props) {
        TimeLimiterConfig config =
                TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofMillis(props.getReadTimeoutMs()))
                        .cancelRunningFuture(true)
                        .build();
        return TimeLimiterRegistry.of(config);
    }
}
