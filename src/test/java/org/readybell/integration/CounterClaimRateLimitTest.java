package org.readybell.integration;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guessing receipt numbers is throttled by the production rule in application.yml: 10 tries a minute per IP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "readybell.rate-limit.enabled=true",
        "readybell.rate-limit.trusted-proxies=127.0.0.1"})
class CounterClaimRateLimitTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private org.springframework.test.web.servlet.ResultActions guess(String ip) throws Exception {
        return mvc.perform(post("/api/restaurants/some-cafe/claim").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderNumber\":42}"));
    }

    @Test
    void theEleventhGuessWithinAMinuteIsRejected() throws Exception {
        for (int i = 0; i < 10; i++) {
            guess("198.51.100.7").andExpect(status().isNotFound());
        }
        guess("198.51.100.7").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        guess("198.51.100.8").andExpect(status().isNotFound());   // another client is unaffected
    }
}
