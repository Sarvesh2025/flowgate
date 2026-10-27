package com.flowgate.gateway.controlplane;

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
            RedisConnectionFactory connectionFactory, RuleCache ruleCache) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener = (message, pattern) -> ruleCache.reload();
        container.addMessageListener(listener, new ChannelTopic(ConfigChangePublisher.CHANNEL));
        return container;
    }
}
