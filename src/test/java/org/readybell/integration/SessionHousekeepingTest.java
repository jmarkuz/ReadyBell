package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.platform.SessionHousekeeping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionHousekeepingTest {

    @Autowired private MockMvc mvc;
    @Autowired private SessionHousekeeping housekeeping;
    @MockBean private NotificationSender notificationSender;

    private String createOrder() throws Exception {
        return mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
    }

    private void move(String orderId, String status) throws Exception {
        mvc.perform(patch("/api/orders/" + orderId + "/status").with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void endsOnlyFinishedSessionsAndDropsTheirSubscriptions() throws Exception {
        String activeBody = createOrder();          // stays CREATED
        String doneBody = createOrder();
        String doneSession = JsonPath.read(doneBody, "$.sessionId");
        String doneOrder = JsonPath.read(doneBody, "$.id");
        mvc.perform(post("/api/sessions/" + doneSession + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpointUrl\":\"https://push.example/x\",\"p256dhKey\":\"k\",\"authKey\":\"a\"}"))
                .andExpect(status().isCreated());
        move(doneOrder, "IN_PROGRESS");
        move(doneOrder, "READY");
        move(doneOrder, "COMPLETED");

        int[] result = housekeeping.run(Duration.ofNanos(1));

        // at least our finished session ended (other tests may leave finished sessions too)
        assertEquals(true, result[0] >= 1 && result[1] >= 1);
        mvc.perform(get("/api/sessions/" + doneSession + "/track"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionActive").value(false));
        String activeSession = JsonPath.read(activeBody, "$.sessionId");
        mvc.perform(get("/api/sessions/" + activeSession + "/track"))
                .andExpect(jsonPath("$.sessionActive").value(true));
        // an ended session accepts no more orders
        mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":\"" + doneSession + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void youngSessionsAreLeftAlone() throws Exception {
        String body = createOrder();
        String session = JsonPath.read(body, "$.sessionId");
        housekeeping.run(Duration.ofHours(24));
        mvc.perform(get("/api/sessions/" + session + "/track")).andExpect(jsonPath("$.sessionActive").value(true));
    }
}
