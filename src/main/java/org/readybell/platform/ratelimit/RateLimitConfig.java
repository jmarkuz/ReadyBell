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

    /** One resolver for everything that keys on the client address (rate limits, login throttle). */
    @Bean
    public ClientIpResolver clientIpResolver(RateLimitProperties props) {
        return new ClientIpResolver(props.getTrustedProxies());
    }

    /** Counters of the per-IP limits and of the claim guess protection; Redis shares them between replicas. */
    @Bean
    public FailureStore rateLimitStore(RateLimitProperties props, ObjectProvider<StringRedisTemplate> redis) {
        // Redis keeps the limit shared between replicas and falls back to per-node counters if it is down
        var redisTemplate = props.isRedis() ? redis.getIfAvailable() : null;
        return redisTemplate != null
                ? new RedisFailureStore(redisTemplate, "readybell:rate-limit:")
                : new InMemoryFailureStore();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties props, Clock clock,
            MeterRegistry meters, ClientIpResolver ips, FailureStore rateLimitStore) {
        var filter = new RateLimitFilter(props, ips, rateLimitStore, clock, meters);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(props.isEnabled());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ClaimFailureFilter> claimFailureFilter(RateLimitProperties props, Clock clock,
            MeterRegistry meters, ClientIpResolver ips, FailureStore rateLimitStore) {
        var filter = new ClaimFailureFilter(props.getClaim(), ips, rateLimitStore, clock, meters);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(props.isEnabled());
        registration.addUrlPatterns("/api/restaurants/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
