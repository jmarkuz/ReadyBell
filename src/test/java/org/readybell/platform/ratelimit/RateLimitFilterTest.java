package org.readybell.platform.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.RedisFailureStore;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    @Test
    void redisDownFallsBackToALocalLimit() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForZSet()).thenThrow(new RedisConnectionFailureException("down"));
        when(redis.expire(any(String.class), any(Duration.class))).thenThrow(new RedisConnectionFailureException("down"));

        var rule = new RateLimitProperties.Rule();
        rule.setName("session-start");
        rule.setMethod("POST");
        rule.setPath("/api/sessions");
        rule.setLimit(2);
        var props = new RateLimitProperties();
        props.setRules(List.of(rule));
        var filter = new RateLimitFilter(props, new ClientIpResolver(List.of()),
                new RedisFailureStore(redis, "t:"), Clock.systemUTC(), new SimpleMeterRegistry());

        int[] statuses = new int[3];
        for (int i = 0; i < 3; i++) {
            var request = new MockHttpServletRequest("POST", "/api/sessions");
            request.setRemoteAddr("203.0.113.5");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            statuses[i] = response.getStatus();
        }
        assertThat(statuses).containsExactly(200, 200, 429);
    }
}
