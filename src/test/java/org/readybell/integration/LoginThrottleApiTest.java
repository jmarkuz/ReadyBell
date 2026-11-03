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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own context (different property) so the limiter state is not shared with other tests. */
@SpringBootTest(properties = "readybell.security.jwt.login.max-failures=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginThrottleApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private static RequestPostProcessor from(String address) {
        return request -> { request.setRemoteAddr(address); return request; };
    }

    private org.springframework.test.web.servlet.ResultActions login(String user, String pass, String address) throws Exception {
        return mvc.perform(post("/api/login").with(from(address)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"));
    }

    @Test
    void blocksAfterRepeatedFailuresEvenWithCorrectPassword() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("other", "wrong", "10.0.0." + i).andExpect(status().isUnauthorized());
        }
        // account is now locked for everyone, from any address, including the right password
        login("other", "other", "10.0.1.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("urn:readybell:auth:too-many-attempts"));
    }

    @Test
    void otherAccountsAreUnaffectedAndSuccessResetsCounter() throws Exception {
        login("admin", "wrong", "10.0.2.1").andExpect(status().isUnauthorized());
        login("admin", "wrong", "10.0.2.2").andExpect(status().isUnauthorized());
        login("admin", "admin", "10.0.2.3").andExpect(status().isOk());      // success clears username counter
        login("admin", "wrong", "10.0.2.4").andExpect(status().isUnauthorized());
        login("admin", "admin", "10.0.2.5").andExpect(status().isOk());
    }

    @Test
    void sameAddressIsThrottledAcrossUsernames() throws Exception {
        for (String u : new String[]{"u1", "u2", "u3"}) {
            login(u, "x", "10.0.3.1").andExpect(status().isUnauthorized());
        }
        login("platform", "platform", "10.0.3.1").andExpect(status().isTooManyRequests());
        login("platform", "platform", "10.0.3.2").andExpect(status().isOk());
    }
}
