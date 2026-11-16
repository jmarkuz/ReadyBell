package org.readybell.platform.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.InMemoryFailureStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimFailureFilterTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RateLimitProperties.Claim limits = new RateLimitProperties.Claim();
    private final ClaimFailureFilter filter = new ClaimFailureFilter(limits, new ClientIpResolver(List.of()),
            new InMemoryFailureStore(), Clock.systemUTC(), meters);

    /** What the controller answers; the filter only looks at the status. */
    private int answer = 404;
    private final AtomicInteger reachedController = new AtomicInteger();
    private final FilterChain controller = (req, res) -> {
        reachedController.incrementAndGet();
        ((MockHttpServletResponse) res).setStatus(answer);
    };

    private MockHttpServletResponse claim(String ip, String uri) throws Exception {
        var request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, controller);
        return response;
    }

    @Test
    void tenFailedGuessesFromOneAddressBlockTheEleventh() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(claim("203.0.113.5", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(404);
        }
        var blocked = claim("203.0.113.5", "/api/restaurants/cafe/claim");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(reachedController.get()).isEqualTo(10);                      // the 11th never reached the controller
        assertThat(meters.counter("ratelimit.rejected", "endpoint", "counter-claim").count()).isEqualTo(1);
        assertThat(meters.counter("claim.failures").count()).isEqualTo(10);
    }

    @Test
    void successfulClaimsAreNeverCounted() throws Exception {
        answer = 200;
        // a queue of customers behind one restaurant Wi-Fi: 40 successful claims from one address
        for (int i = 0; i < 40; i++) {
            assertThat(claim("203.0.113.5", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(200);
        }
        assertThat(meters.counter("claim.failures").count()).isZero();
        answer = 404;
        assertThat(claim("203.0.113.5", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(404);   // not blocked
    }

    @Test
    void aMalformedNumberCountsAsAFailureToo() throws Exception {
        answer = 400;
        for (int i = 0; i < 10; i++) {
            claim("203.0.113.5", "/api/restaurants/cafe/claim");
        }
        assertThat(claim("203.0.113.5", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(429);
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        for (int i = 0; i < 10; i++) {
            claim("203.0.113.5", "/api/restaurants/cafe/claim");
        }
        assertThat(claim("203.0.113.5", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(429);
        assertThat(claim("203.0.113.6", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(404);
    }

    @Test
    void aDistributedGuesserHitsTheRestaurantWideLimit() throws Exception {
        // 100 failures from 100 different addresses, each far below the per-address limit
        for (int i = 0; i < 100; i++) {
            assertThat(claim("198.51.100." + (i + 1), "/api/restaurants/cafe/claim").getStatus()).isEqualTo(404);
        }
        assertThat(claim("198.51.100.200", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(429);   // a fresh address, same restaurant
        assertThat(claim("198.51.100.200", "/api/restaurants/bar/claim").getStatus()).isEqualTo(404);    // other restaurants unaffected
    }

    @Test
    void differentSpellingsOfThePathShareTheCounters() throws Exception {
        for (int i = 0; i < 5; i++) {
            claim("203.0.113.5", "/api/restaurants/cafe/%63laim");              // percent-encoded letter
            claim("203.0.113.5", "//api/restaurants/CAFE/claim");               // doubled slash, other case of the restaurant
        }
        assertThat(claim("203.0.113.5", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(429);
    }

    @Test
    void otherEndpointsAreLeftAlone() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertThat(claim("203.0.113.5", "/api/restaurants/cafe/public").getStatus()).isEqualTo(404);
        }
        assertThat(claim("203.0.113.5", "/api/restaurants/cafe/claim").getStatus()).isEqualTo(404);
    }
}
