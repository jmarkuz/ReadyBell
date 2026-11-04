package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/orders/metrics: real per-status totals, tenant-isolated. The database is shared, so tests assert deltas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderMetricsApiTest {

    private static final TenantId TENANT_A = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

    @Autowired private MockMvc mvc;
    @Autowired private CustomerSessionRepository sessions;
    @MockBean private NotificationSender notificationSender;

    private String metrics(String user) throws Exception {
        return mvc.perform(get("/api/orders/metrics").with(httpBasic(user, user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int field(String json, String name) {
        return JsonPath.read(json, "$." + name);
    }

    private String createOrder() throws Exception {
        var session = CustomerSession.start(TENANT_A, Clock.systemUTC());
        sessions.save(session);
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerSessionId\":\"" + session.getId().value() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void transition(String id, String to) throws Exception {
        mvc.perform(patch("/api/orders/{id}/status", id).with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + to + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void countsFollowOrderStatuses() throws Exception {
        String before = metrics("admin");

        createOrder();                                   // stays CREATED
        String inProgress = createOrder();
        transition(inProgress, "IN_PROGRESS");
        String cancelled = createOrder();
        transition(cancelled, "CANCELLED");

        String after = metrics("admin");
        assertThat(field(after, "totalOrders")).isEqualTo(field(before, "totalOrders") + 3);
        assertThat(field(after, "created")).isEqualTo(field(before, "created") + 1);
        assertThat(field(after, "inProgress")).isEqualTo(field(before, "inProgress") + 1);
        assertThat(field(after, "cancelled")).isEqualTo(field(before, "cancelled") + 1);
        assertThat(field(after, "ready")).isEqualTo(field(before, "ready"));
        assertThat(field(after, "completed")).isEqualTo(field(before, "completed"));
    }

    @Test
    void doesNotIncludeOtherTenantsOrders() throws Exception {
        String otherBefore = metrics("other");
        createOrder();                                   // tenant A
        String otherAfter = metrics("other");
        assertThat(field(otherAfter, "totalOrders")).isEqualTo(field(otherBefore, "totalOrders"));
    }
}
