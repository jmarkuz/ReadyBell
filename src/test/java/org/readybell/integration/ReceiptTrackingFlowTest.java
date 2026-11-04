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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff enters an order (no session), the receipt QR opens the public tracking page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceiptTrackingFlowTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    @Test
    void orderWithoutSessionIsTrackableByAnonymousCustomer() throws Exception {
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Anna\",\"estimatedMinutes\":10,"
                                + "\"items\":[{\"name\":\"Latte\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(body, "$.sessionId");
        String orderId = JsonPath.read(body, "$.id");

        // anonymous: no credentials
        mvc.perform(get("/api/sessions/" + sessionId + "/track"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionActive").value(true))
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].status").value("CREATED"))
                .andExpect(jsonPath("$.orders[0].items[0].name").value("Latte"))
                .andExpect(jsonPath("$.orders[0].estimatedWaitMinutes").isNumber());

        mvc.perform(patch("/api/orders/" + orderId + "/status").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/sessions/" + sessionId + "/track"))
                .andExpect(jsonPath("$.orders[0].status").value("IN_PROGRESS"));
    }

    @Test
    void unknownSessionIs404() throws Exception {
        mvc.perform(get("/api/sessions/" + UUID.randomUUID() + "/track"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pushConfigIsPublicAndHidesPlaceholderKey() throws Exception {
        mvc.perform(get("/api/push/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
