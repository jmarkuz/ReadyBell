package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.adapter.in.web.sse.SseEmitterRegistry;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.testsupport.RecordingEmitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Handing the QR over in person: the first time the customer opens the tracking link the staff are told ("connected"),
 * once; the staff list carries the session and the flag; the customer never sees the staff event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderConnectedApiTest {

    private static final TenantId ADMIN_TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

    @Autowired private MockMvc mvc;
    @Autowired private SseEmitterRegistry registry;
    @MockBean private NotificationSender notificationSender;

    private String createOrder() throws Exception {
        return mvc.perform(post("/api/orders").with(httpBasic("admin", "admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Connect\",\"items\":[{\"name\":\"Latte\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String row(String orderId) throws Exception {
        return mvc.perform(get("/api/orders").param("search", "Connect").param("pageSize", "100").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .transform(body -> {
                    java.util.List<java.util.Map<String, Object>> rows = JsonPath.read(body, "$.items[?(@.id=='" + orderId + "')]");
                    return rows.get(0).toString();
                });
    }

    @Test
    void theFirstOpenOfTheLinkTellsTheStaffOnceAndTheListShowsIt() throws Exception {
        String created = createOrder();
        String orderId = JsonPath.read(created, "$.id");
        String sessionId = JsonPath.read(created, "$.sessionId");
        String number = JsonPath.read(created, "$.orderId");
        var staff = new RecordingEmitter();
        var customer = new RecordingEmitter();
        registry.registerStaff(ADMIN_TENANT, staff);
        registry.registerCustomer(new CustomerSessionId(UUID.fromString(sessionId)), customer);

        assertThat(row(orderId)).contains("connected=false").contains("sessionId=" + sessionId);

        mvc.perform(get("/api/sessions/" + sessionId + "/track")).andExpect(status().isOk());
        mvc.perform(get("/api/sessions/" + sessionId + "/track")).andExpect(status().isOk());   // the page polls

        awaitConnected(staff);
        assertThat(count(staff.text(), "\"event\":\"order-connected\"")).isEqualTo(1);   // the payload, once
        assertThat(staff.text()).contains("\"orderNumber\":\"" + number + "\"").contains("\"id\":\"" + orderId + "\"");
        assertThat(customer.text()).doesNotContain("order-connected");           // staff only
        assertThat(row(orderId)).contains("connected=true");
    }

    @Test
    void theStaffListNeverLeaksAnotherRestaurantsSession() throws Exception {
        String created = createOrder();
        String sessionId = JsonPath.read(created, "$.sessionId");
        mvc.perform(get("/api/orders").param("pageSize", "100").with(httpBasic("other", "other")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(sessionId));
    }

    @Test
    void trackingAnUnknownLinkSignalsNothing() throws Exception {
        var staff = new RecordingEmitter();
        registry.registerStaff(ADMIN_TENANT, staff);

        mvc.perform(get("/api/sessions/" + UUID.randomUUID() + "/track")).andExpect(status().isNotFound());

        assertThat(staff.text()).doesNotContain("order-connected");
    }

    private static void awaitConnected(RecordingEmitter emitter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!emitter.text().contains("order-connected") && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
