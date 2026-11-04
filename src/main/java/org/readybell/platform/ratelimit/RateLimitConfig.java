package org.readybell.platform.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.readybell.platform.auth.FailureStore;
import org.readybell.platform.auth.InMemoryFailureStore;
import org.readybell.platform.auth.RedisFailureStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/** Registers the public-endpoint rate limiter ahead of Spring Security. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties props, Clock clock,
            MeterRegistry meters, ObjectProvider<StringRedisTemplate> redis) {
        // Redis keeps the limit shared between replicas and falls back to per-node counters if it is down
        var redisTemplate = props.isRedis() ? redis.getIfAvailable() : null;
        FailureStore store = redisTemplate != null
                ? new RedisFailureStore(redisTemplate, "readybell:rate-limit:")
                : new InMemoryFailureStore();
        var filter = new RateLimitFilter(props, new ClientIpResolver(props.getTrustedProxies()), store, clock, meters);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(props.isEnabled());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
