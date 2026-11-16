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

    private static RateLimitFilter filterWith(String name, String method, String path, int limit) {
        var rule = new RateLimitProperties.Rule();
        rule.setName(name);
        rule.setMethod(method);
        rule.setPath(path);
        rule.setLimit(limit);
        var props = new RateLimitProperties();
        props.setRules(List.of(rule));
        return new RateLimitFilter(props, new ClientIpResolver(List.of()), new org.readybell.platform.auth.InMemoryFailureStore(),
                Clock.systemUTC(), new SimpleMeterRegistry());
    }

    private static int call(RateLimitFilter filter, String method, String uri) throws Exception {
        var request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("203.0.113.9");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    /** Tomcat decodes the path before routing, so the same endpoint can be spelled several ways; all share one limit. */
    @Test
    void differentSpellingsOfThePathShareTheLimit() throws Exception {
        var filter = filterWith("session-start", "POST", "/api/sessions", 3);

        assertThat(call(filter, "POST", "/api/%73essions")).isEqualTo(200);      // percent-encoded letter
        assertThat(call(filter, "POST", "//api/sessions")).isEqualTo(200);       // doubled slash
        assertThat(call(filter, "POST", "/api/sessions;x=1")).isEqualTo(200);    // path parameter
        assertThat(call(filter, "POST", "/api/sessions")).isEqualTo(429);
        assertThat(call(filter, "POST", "/api/%73essions")).isEqualTo(429);
    }

    @Test
    void anEncodedSegmentInsideAWildcardRuleIsStillLimited() throws Exception {
        var filter = filterWith("counter-claim", "POST", "/api/restaurants/*/claim", 2);

        assertThat(call(filter, "POST", "/api/restaurants/cafe/%63laim")).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/restaurants/%63afe/claim")).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/restaurants/cafe/claim")).isEqualTo(429);
    }

    @Test
    void otherPathsAndMethodsAreNotCounted() throws Exception {
        var filter = filterWith("session-start", "POST", "/api/sessions", 1);

        assertThat(call(filter, "GET", "/api/sessions")).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/orders")).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/sessions")).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/sessions")).isEqualTo(429);
    }
}
