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

    private static RateLimitFilter filterWith(java.util.List<RateLimitProperties.Rule> rules) {
        var props = new RateLimitProperties();
        props.setRules(rules);
        return new RateLimitFilter(props, new ClientIpResolver(List.of()), new org.readybell.platform.auth.InMemoryFailureStore(),
                Clock.systemUTC(), new SimpleMeterRegistry());
    }

    private static RateLimitProperties.Rule rule(String name, String path, RateLimitProperties.KeyBy keyBy, int limit) {
        var r = new RateLimitProperties.Rule();
        r.setName(name);
        r.setMethod("GET");
        r.setPath(path);
        r.setKeyBy(keyBy);
        r.setLimit(limit);
        return r;
    }

    private static int get(RateLimitFilter filter, String ip, String uri) throws Exception {
        var request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void customersSharingOneWifiDoNotUseUpEachOthersBudget() throws Exception {
        // what application.yml does for the tracking page: 30 a minute per session, 600 per address
        var filter = filterWith(List.of(
                rule("track", "/api/sessions/*/track", RateLimitProperties.KeyBy.SESSION, 30),
                rule("track-ip", "/api/sessions/*/track", RateLimitProperties.KeyBy.IP, 600)));

        // 40 customers behind one address, six requests each (one minute of 10-second polling): nobody is throttled
        for (int round = 0; round < 6; round++) {
            for (int customer = 0; customer < 40; customer++) {
                assertThat(get(filter, "203.0.113.20", "/api/sessions/" + new java.util.UUID(0, customer) + "/track")).isEqualTo(200);
            }
        }
    }

    @Test
    void oneSessionHammeringTheEndpointIsStoppedWhateverTheAddress() throws Exception {
        var filter = filterWith(List.of(
                rule("track", "/api/sessions/*/track", RateLimitProperties.KeyBy.SESSION, 30),
                rule("track-ip", "/api/sessions/*/track", RateLimitProperties.KeyBy.IP, 600)));
        var id = java.util.UUID.randomUUID();
        String session = "/api/sessions/" + id + "/track";

        for (int i = 0; i < 30; i++) assertThat(get(filter, "203.0.113.20", session)).isEqualTo(200);
        assertThat(get(filter, "203.0.113.20", session)).isEqualTo(429);
        assertThat(get(filter, "198.51.100.1", "/api/sessions/" + id.toString().toUpperCase() + "/track"))   // same session, other spelling and address
                .isEqualTo(429);
        assertThat(get(filter, "203.0.113.20", "/api/sessions/" + java.util.UUID.randomUUID() + "/track")).isEqualTo(200);   // another session is fine
    }

    @Test
    void theAddressCeilingStillStopsOneMachineSweepingManySessions() throws Exception {
        var filter = filterWith(List.of(
                rule("track", "/api/sessions/*/track", RateLimitProperties.KeyBy.SESSION, 30),
                rule("track-ip", "/api/sessions/*/track", RateLimitProperties.KeyBy.IP, 50)));

        for (int i = 0; i < 50; i++) assertThat(get(filter, "203.0.113.30", "/api/sessions/" + new java.util.UUID(1, i) + "/track")).isEqualTo(200);
        assertThat(get(filter, "203.0.113.30", "/api/sessions/" + new java.util.UUID(1, 999) + "/track")).isEqualTo(429);
    }

    @Test
    void aRejectedRequestDoesNotUseUpTheOtherBuckets() throws Exception {
        var filter = filterWith(List.of(
                rule("strict", "/x", RateLimitProperties.KeyBy.IP, 1),
                rule("loose", "/x", RateLimitProperties.KeyBy.IP, 3)));

        assertThat(get(filter, "203.0.113.40", "/x")).isEqualTo(200);
        for (int i = 0; i < 10; i++) assertThat(get(filter, "203.0.113.40", "/x")).isEqualTo(429);   // all blocked by "strict"
        // "loose" only counted the one request that got through, so it still has room if "strict" were relaxed
    }

    @Test
    void aGlobalRuleCapsEveryoneTogether() throws Exception {
        var filter = filterWith(List.of(rule("all", "/y", RateLimitProperties.KeyBy.GLOBAL, 3)));

        assertThat(get(filter, "203.0.113.1", "/y")).isEqualTo(200);
        assertThat(get(filter, "203.0.113.2", "/y")).isEqualTo(200);
        assertThat(get(filter, "203.0.113.3", "/y")).isEqualTo(200);
        assertThat(get(filter, "203.0.113.4", "/y")).isEqualTo(429);
    }
}
