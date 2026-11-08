package com.flowgate.gateway.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Per-process fallback when Redis is down and the rule is FAIL_OPEN. */
@Component
public class LocalWindowLimiter {

    private record Window(long startMs, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public Decision allow(String key, int capacity, long windowMs) {
        long now = System.currentTimeMillis();
        Window next =
                windows.compute(
                        key,
                        (k, current) -> {
                            if (current == null || now - current.startMs >= windowMs) {
                                return new Window(now, 1);
                            }
                            return new Window(current.startMs, current.count + 1);
                        });
        int remaining = Math.max(0, capacity - next.count);
        return new Decision(next.count <= capacity, remaining);
    }
}
