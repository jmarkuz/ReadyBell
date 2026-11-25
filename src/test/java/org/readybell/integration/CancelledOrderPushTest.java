package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.platform.outbox.OutboxRelay;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A customer who is waiting is told when the order is cancelled, through the same outbox and delivery path as READY. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancelledOrderPushTest {

    @Autowired private MockMvc mvc;
    @Autowired private OutboxRelay relay;
    @MockBean private NotificationSender sender;

    @Test
    void cancellingAnOrderPushesToTheCustomerDevice() throws Exception {
        when(sender.send(any(), any())).thenReturn(SendResult.ACCEPTED);
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerName\":\"Anna\"}"))
                .andReturn().getResponse().getContentAsString();
        String session = JsonPath.read(body, "$.sessionId");
        String order = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/sessions/" + session + "/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpointUrl\":\"https://fcm.googleapis.com/fcm/send/c\",\"p256dhKey\":\"k\",\"authKey\":\"a\"}"))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(patch("/api/orders/" + order + "/status").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
        relay.drain();

        var payload = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(sender, atLeastOnce()).send(any(), payload.capture());
        assertThat(payload.getAllValues()).anySatisfy(p -> {
            assertThat(p.body()).contains("was cancelled");
            assertThat(p.url()).isEqualTo("/track/" + session);
        });
    }
}
