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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The operator can see how the server resolves a client address, which is how a proxy setup is verified. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientIpDiagnosticsApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    @Test
    void theOperatorSeesTheResolvedClientAndWhetherAProxyIsTrusted() throws Exception {
        mvc.perform(get("/api/platform/diagnostics/client-ip").with(httpBasic("platform", "platform"))
                        .with(r -> { r.setRemoteAddr("172.18.0.1"); return r; })
                        .header("X-Forwarded-For", "203.0.113.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peer").value("172.18.0.1"))
                .andExpect(jsonPath("$.clientIp").value("172.18.0.1"))        // no trusted proxies in the test profile
                .andExpect(jsonPath("$.forwardedFor").value("203.0.113.9"))
                .andExpect(jsonPath("$.trustedProxiesConfigured").value(false))
                .andExpect(jsonPath("$.peerIsTrustedProxy").value(false));
    }

    @Test
    void itIsForTheOperatorOnly() throws Exception {
        mvc.perform(get("/api/platform/diagnostics/client-ip")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/diagnostics/client-ip").with(httpBasic("admin", "admin")))
                .andExpect(status().isForbidden());
    }
}
