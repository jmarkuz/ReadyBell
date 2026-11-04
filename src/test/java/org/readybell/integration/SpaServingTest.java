package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The UI shell is public and deep links fall back to index.html; the API is untouched. */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@ActiveProfiles("test")
class SpaServingTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    @Test
    void deepLinkServesIndexWithoutSignIn() throws Exception {
        mvc.perform(get("/track/0b3c0c1e-0000-0000-0000-000000000000"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("spa-test-shell")));
        mvc.perform(get("/orders")).andExpect(status().isOk());
    }

    @Test
    void unknownApiPathIsNotRewrittenToIndex() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("spa-test-shell"))));
    }

    @Test
    void apiStillNeedsAuthentication() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingAssetIs404() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    void securityHeadersAreSet() throws Exception {
        mvc.perform(get("/track/x"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
    }

    @Test
    void metricsAreNotPublic() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("admin", "admin"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(httpBasic("platform", "platform")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
    }
}
