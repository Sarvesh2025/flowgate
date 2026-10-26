package com.flowgate.gateway.ratelimit;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public TokenBucketLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        script.setResultType(List.class);
    }

    @Override
    public Decision allow(String key, int capacity, long windowMs) {
        long now = System.currentTimeMillis();
        String refillPerMs = Double.toString((double) capacity / (double) windowMs);
        String ttlMs = Long.toString(Math.max(windowMs * 3, 1_000L));
        List<?> raw = redis.execute(
                script,
                List.of("rl:tb:" + key),
                Integer.toString(capacity),
                refillPerMs,
                Long.toString(now),
                "1",
                ttlMs);
        if (raw == null || raw.size() < 2) {
            throw new IllegalStateException("token bucket script returned " + raw);
        }
        long allowed = Long.parseLong(String.valueOf(raw.get(0)));
        long remaining = Long.parseLong(String.valueOf(raw.get(1)));
        return new Decision(allowed == 1, remaining);
    }
}
