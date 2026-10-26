package com.flowgate.gateway.ratelimit;

public interface RateLimiter {

    Decision allow(String key, int capacity, long windowMs);
}
