package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behind a reverse proxy every request arrives from the proxy's address. The per-address login throttle must key on
 * the real client (X-Forwarded-For from a trusted proxy), or one noisy client locks everyone out.
 */
@SpringBootTest(properties = {
        "readybell.security.jwt.login.max-failures=3",
        "readybell.security.jwt.login.max-failures-per-address=3",
        "readybell.rate-limit.trusted-proxies=10.0.0.0/8"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginThrottleBehindProxyTest {

    private static final String PROXY = "10.1.1.1";

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private ResultActions login(String user, String pass, String client) throws Exception {
        return mvc.perform(post("/api/login")
                .with(request -> { request.setRemoteAddr(PROXY); return request; })
                .header("X-Forwarded-For", client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"));
    }

    @Test
    void oneClientBehindTheProxyDoesNotLockOutAnother() throws Exception {
        for (String u : new String[]{"u1", "u2", "u3"}) {
            login(u, "x", "203.0.113.1").andExpect(status().isUnauthorized());
        }
        login("platform", "platform", "203.0.113.1").andExpect(status().isTooManyRequests());
        login("platform", "platform", "203.0.113.2").andExpect(status().isOk());
    }
}
