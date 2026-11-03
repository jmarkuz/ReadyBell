package org.readybell.ordering.adapter.in.web.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Relays order status changes between nodes over Redis pub/sub (channel {@value #CHANNEL}).
 * Active only with {@code readybell.sse.redis.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "readybell.sse.redis.enabled", havingValue = "true")
public class RedisSseBridge {

    static final String CHANNEL = "readybell:order-status";

    private static final Logger log = LoggerFactory.getLogger(RedisSseBridge.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RedisMessageListenerContainer container;

    public RedisSseBridge(StringRedisTemplate redis, ObjectMapper mapper,
                          RedisConnectionFactory connectionFactory, SseEmitterRegistry registry) {
        this.redis = redis;
        this.mapper = mapper;
        this.container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener = (message, pattern) -> {
            try {
                registry.deliver(mapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), SseMessage.class));
            } catch (Exception e) {
                log.warn("Ignoring malformed SSE relay message: {}", e.getMessage());
            }
        };
        container.addMessageListener(listener, new ChannelTopic(CHANNEL));
        container.afterPropertiesSet();
        container.start();
    }

    /** @return {@code true} if the message was handed to Redis, {@code false} if the caller should deliver locally. */
    boolean publish(SseMessage message) {
        try {
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(message));
            return true;
        } catch (Exception e) {
            log.warn("Redis SSE relay unavailable, delivering to local clients only: {}", e.getMessage());
            return false;
        }
    }

    @jakarta.annotation.PreDestroy
    void stop() throws Exception {
        container.stop();
        container.destroy();
    }
}
