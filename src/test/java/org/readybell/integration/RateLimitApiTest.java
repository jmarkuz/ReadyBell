package org.readybell.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public endpoints are limited per client IP: request N+1 in the window gets 429 with Retry-After. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "readybell.rate-limit.enabled=true",
        "readybell.rate-limit.trusted-proxies=127.0.0.1",
        "readybell.rate-limit.rules[0].name=session-start",
        "readybell.rate-limit.rules[0].method=POST",
        "readybell.rate-limit.rules[0].path=/api/sessions",
        "readybell.rate-limit.rules[0].limit=3",
        "readybell.rate-limit.rules[0].window=1m",
        "readybell.rate-limit.rules[1].name=track",
        "readybell.rate-limit.rules[1].method=GET",
        "readybell.rate-limit.rules[1].path=/api/sessions/*/track",
        "readybell.rate-limit.rules[1].limit=120",
        "readybell.rate-limit.rules[1].window=1m"})
class RateLimitApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private MeterRegistry meters;
    @MockBean private NotificationSender notificationSender;

    private ResultActions startSession(String clientIp) throws Exception {
        var request = post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"x\"}")
                .header("X-Forwarded-For", clientIp);
        return mvc.perform(request);
    }

    @Test
    void requestOverTheLimitGets429WithRetryAfterAndProblemBody() throws Exception {
        String ip = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            startSession(ip).andExpect(status().is(not(429)));
        }
        double before = meters.counter("ratelimit.rejected", "endpoint", "session-start").count();

        startSession(ip).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("problem+json")))
                .andExpect(jsonPath("$.status").value(429));

        assertThat(meters.counter("ratelimit.rejected", "endpoint", "session-start").count()).isEqualTo(before + 1);
    }

    @Test
    void differentClientsDoNotAffectEachOther() throws Exception {
        for (int i = 0; i < 4; i++) {
            startSession("203.0.113.20");
        }
        startSession("203.0.113.20").andExpect(status().isTooManyRequests());
        startSession("203.0.113.21").andExpect(status().is(not(429)));
    }

    @Test
    void forwardedHeaderIsIgnoredFromUntrustedPeers() throws Exception {
        // A spoofed leftmost address cannot buy a fresh budget: the right-most untrusted hop counts
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"x\"}")
                    .header("X-Forwarded-For", "1.1.1." + i + ", 203.0.113.30"));
        }
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"x\"}")
                        .header("X-Forwarded-For", "9.9.9.9, 203.0.113.30"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void otherPublicEndpointsHaveTheirOwnLimits() throws Exception {
        String session = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/sessions/{id}/track", session).header("X-Forwarded-For", "203.0.113.40"))
                    .andExpect(status().is(not(429)));
        }
        // track allows 120, so still fine; the session-start budget of this IP is untouched
        startSession("203.0.113.40").andExpect(status().is(not(429)));
    }

    @Test
    void staffEndpointsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/orders").header("X-Forwarded-For", "203.0.113.50"))
                    .andExpect(status().isUnauthorized());     // 401, never 429
        }
    }
}
