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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The staff SSE stream opens with a single-use ticket; the JWT is never accepted in the URL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StreamTicketApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private String bearerLogin(String user) throws Exception {
        String body = mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + user + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String issueTicket(String jwt) throws Exception {
        String body = mvc.perform(post("/api/orders/stream/ticket").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ticket");
    }

    @Test
    void ticketOpensTheStreamOnceOnly() throws Exception {
        String ticket = issueTicket(bearerLogin("admin"));

        mvc.perform(get("/api/orders/stream").param("ticket", ticket))
                .andExpect(request().asyncStarted());

        mvc.perform(get("/api/orders/stream").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticketRequestNeedsAuthentication() throws Exception {
        mvc.perform(post("/api/orders/stream/ticket")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTicketIsRejected() throws Exception {
        mvc.perform(get("/api/orders/stream").param("ticket", "nope")).andExpect(status().isUnauthorized());
    }

    @Test
    void jwtInTheUrlIsNoLongerAccepted() throws Exception {
        mvc.perform(get("/api/orders/stream").param("token", bearerLogin("admin")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticketIsNotAcceptedOnOtherEndpoints() throws Exception {
        String ticket = issueTicket(bearerLogin("admin"));
        mvc.perform(get("/api/orders").param("ticket", ticket)).andExpect(status().isUnauthorized());
        // and it was not consumed by the rejected call
        mvc.perform(get("/api/orders/stream").param("ticket", ticket)).andExpect(request().asyncStarted());
    }

    @Test
    void basicAuthCallersGetATicketToo() throws Exception {
        mvc.perform(post("/api/orders/stream/ticket").with(httpBasic("other", "other")))
                .andExpect(status().isOk());
    }
}
