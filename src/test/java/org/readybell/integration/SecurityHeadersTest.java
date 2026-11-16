package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** Browser-facing hardening headers on API and page responses. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    @Test
    void theTrackingUrlIsNeverSentToOtherSitesInReferer() throws Exception {
        mvc.perform(get("/api/push/config")).andExpect(header().string("Referrer-Policy", "no-referrer"));
        mvc.perform(get("/track/00000000-0000-0000-0000-000000000000")).andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void sensitiveBrowserFeaturesAreSwitchedOff() throws Exception {
        mvc.perform(get("/api/push/config"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Permissions-Policy", containsString("microphone=()")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")));
    }

    @Test
    void hstsIsSentEvenWhenTlsEndsAtTheProxyAndTheAppSeesPlainHttp() throws Exception {
        mvc.perform(get("/api/push/config"))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=15552000")));
    }

    @Test
    void theContentSecurityPolicyAndClickjackingProtectionRemain() throws Exception {
        mvc.perform(get("/api/push/config"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
