package com.flowgate.gateway.ratelimit;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SlidingWindowLogLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public SlidingWindowLogLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/sliding_window_log.lua"));
        script.setResultType(List.class);
    }

    @Override
    public Decision allow(String key, int capacity, long windowMs) {
        long now = System.currentTimeMillis();
        String ttlMs = Long.toString(Math.max(windowMs * 2, 1_000L));
        List<?> raw = redis.execute(
                script,
                List.of("rl:swl:" + key),
                Long.toString(windowMs),
                Integer.toString(capacity),
                Long.toString(now),
                ttlMs,
                Long.toString(System.nanoTime()));
        if (raw == null || raw.size() < 2) {
            throw new IllegalStateException("sliding window log script returned " + raw);
        }
        long allowed = Long.parseLong(String.valueOf(raw.get(0)));
        long remaining = Long.parseLong(String.valueOf(raw.get(1)));
        return new Decision(allowed == 1, remaining);
    }
}
