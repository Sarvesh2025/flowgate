package com.flowgate.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredRateLimiter {

    private final TokenBucketLimiter tokenBucket;
    private final SlidingWindowCounterLimiter slidingWindowCounter;
    private final SlidingWindowLogLimiter slidingWindowLog;
    private final FixedWindowLimiter fixedWindow;
    private final String algorithm;

    public ConfiguredRateLimiter(
            TokenBucketLimiter tokenBucket,
            SlidingWindowCounterLimiter slidingWindowCounter,
            SlidingWindowLogLimiter slidingWindowLog,
            FixedWindowLimiter fixedWindow,
            @Value("${flowgate.ratelimit.algorithm}") String algorithm) {
        this.tokenBucket = tokenBucket;
        this.slidingWindowCounter = slidingWindowCounter;
        this.slidingWindowLog = slidingWindowLog;
        this.fixedWindow = fixedWindow;
        this.algorithm = algorithm;
    }

    public Decision allow(String key, int capacity, long windowMs) {
        return allow(algorithm, key, capacity, windowMs);
    }

    public Decision allow(String chosen, String key, int capacity, long windowMs) {
        return switch (chosen) {
            case "TOKEN_BUCKET" -> tokenBucket.allow(key, capacity, windowMs);
            case "SLIDING_WINDOW_COUNTER" -> slidingWindowCounter.allow(key, capacity, windowMs);
            case "SLIDING_WINDOW_LOG" -> slidingWindowLog.allow(key, capacity, windowMs);
            case "FIXED_WINDOW" -> fixedWindow.allow(key, capacity, windowMs);
            default -> throw new IllegalStateException("unknown algorithm: " + chosen);
        };
    }
}
