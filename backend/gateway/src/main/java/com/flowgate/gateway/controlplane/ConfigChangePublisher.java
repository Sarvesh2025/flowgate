package com.flowgate.gateway.controlplane;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConfigChangePublisher {

    public static final String CHANNEL = "flowgate.config";

    private final StringRedisTemplate redis;

    public ConfigChangePublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void rulesChanged() {
        redis.convertAndSend(CHANNEL, "rules");
    }
}
