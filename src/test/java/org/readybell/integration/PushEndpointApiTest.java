package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The server calls the push endpoint a browser registers, so anything but a real push service is refused (SSRF). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushEndpointApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private String sessionId() throws Exception {
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerName\":\"Push\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.sessionId");
    }

    private ResultActions register(String session, String endpoint) throws Exception {
        return mvc.perform(post("/api/sessions/" + session + "/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpointUrl\":\"" + endpoint + "\",\"p256dhKey\":\"k\",\"authKey\":\"a\"}"));
    }

    @Test
    void internalAndNonHttpsEndpointsAreRefusedWith400() throws Exception {
        String session = sessionId();
        for (String bad : new String[] {
                "http://169.254.169.254/latest/meta-data/", "https://localhost:8080/admin", "https://10.0.0.5/x",
                "https://evil.example/x", "file:///etc/passwd"}) {
            register(session, bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void aRealPushServiceEndpointIsAccepted() throws Exception {
        register(sessionId(), "https://fcm.googleapis.com/fcm/send/abc").andExpect(status().isCreated());
    }

    @Test
    void anOverlongEndpointIsA400NotA500() throws Exception {
        register(sessionId(), "https://fcm.googleapis.com/" + "a".repeat(3000)).andExpect(status().isBadRequest());
    }
}
