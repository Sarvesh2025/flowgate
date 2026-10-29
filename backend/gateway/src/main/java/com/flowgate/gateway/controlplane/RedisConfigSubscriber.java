package com.flowgate.gateway.controlplane;

import com.flowgate.gateway.proxy.RouteCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfigSubscriber {

    @Bean
    RedisMessageListenerContainer configChannel(
            RedisConnectionFactory connectionFactory,
            RuleCache ruleCache,
            RouteCache routeCache) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener =
                (message, pattern) -> {
                    ruleCache.reload();
                    routeCache.reload();
                };
        container.addMessageListener(listener, new ChannelTopic(ConfigChangePublisher.CHANNEL));
        return container;
    }
}
