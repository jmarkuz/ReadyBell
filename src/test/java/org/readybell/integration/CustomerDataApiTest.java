package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer without an account can see and erase their own data with the link to their order, and the push consent is
 * recorded when they subscribe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerDataApiTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/device-secret-token";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private NotificationSender sender;

    private record Placed(String sessionId, String orderId) {}

    private Placed place(String customer, String item) throws Exception {
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"" + customer + "\",\"items\":[{\"name\":\"" + item + "\",\"quantity\":2}]}"))
                .andReturn().getResponse().getContentAsString();
        return new Placed(JsonPath.read(body, "$.sessionId"), JsonPath.read(body, "$.id"));
    }

    private void subscribe(String session) throws Exception {
        mvc.perform(post("/api/sessions/" + session + "/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpointUrl\":\"" + ENDPOINT + "\",\"p256dhKey\":\"k\",\"authKey\":\"a\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private void move(String orderId, String status) throws Exception {
        mvc.perform(patch("/api/orders/" + orderId + "/status").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void subscribingRecordsTheConsent() throws Exception {
        Placed p = place("Anna", "Latte");

        subscribe(p.sessionId());

        var row = jdbc.queryForMap("SELECT consent_at, consent_notice FROM device_subscription WHERE session_id = ?::uuid", p.sessionId());
        assertThat(row.get("consent_at")).isNotNull();
        assertThat(row.get("consent_notice")).isNotNull();
    }

    @Test
    void theCustomerCanDownloadTheirDataWithoutTheDeviceSecrets() throws Exception {
        Placed p = place("Anna Petrenko", "Latte for Anna");
        subscribe(p.sessionId());

        String json = mvc.perform(get("/api/sessions/" + p.sessionId() + "/data"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].customerName").value("Anna Petrenko"))
                .andExpect(jsonPath("$.orders[0].items[0].name").value("Latte for Anna"))
                .andExpect(jsonPath("$.orders[0].anonymized").value(false))
                .andExpect(jsonPath("$.pushSubscriptions.length()").value(1))
                .andExpect(jsonPath("$.pushSubscriptions[0].pushService").value("fcm.googleapis.com"))
                .andExpect(jsonPath("$.pushSubscriptions[0].agreedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("device-secret-token").doesNotContain("\"k\"");
    }

    @Test
    void erasingRemovesTheSubscriptionAndTheNameButAnOrderInProgressKeepsItsItems() throws Exception {
        Placed p = place("Anna Petrenko", "Latte for Anna");
        subscribe(p.sessionId());

        mvc.perform(delete("/api/sessions/" + p.sessionId() + "/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions").value(1))
                .andExpect(jsonPath("$.ordersAnonymized").value(0))
                .andExpect(jsonPath("$.ordersKeptUntilFinished").value(1));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_subscription WHERE session_id = ?::uuid", Integer.class, p.sessionId())).isZero();
        assertThat(jdbc.queryForObject("SELECT customer_name FROM \"order\" WHERE id = ?::uuid", String.class, p.orderId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE order_id = ?::uuid", Integer.class, p.orderId())).isEqualTo(1);
        // the order is still trackable: the kitchen is still preparing it
        mvc.perform(get("/api/sessions/" + p.sessionId() + "/track")).andExpect(status().isOk());
    }

    @Test
    void aFinishedOrderIsAnonymizedAtOnceAndItsFactSurvives() throws Exception {
        Placed p = place("Olena", "Borscht");
        move(p.orderId(), "IN_PROGRESS");
        move(p.orderId(), "READY");
        move(p.orderId(), "COMPLETED");

        mvc.perform(delete("/api/sessions/" + p.sessionId() + "/data"))
                .andExpect(jsonPath("$.ordersAnonymized").value(1));

        var order = jdbc.queryForMap("SELECT customer_name, session_id, anonymized_at FROM \"order\" WHERE id = ?::uuid", p.orderId());
        assertThat(order.get("customer_name")).isNull();
        assertThat(order.get("session_id").toString()).isNotEqualTo(p.sessionId());
        assertThat(order.get("anonymized_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE order_id = ?::uuid", Integer.class, p.orderId())).isZero();
        var fact = jdbc.queryForMap("SELECT outcome, quantity, approximate FROM order_fact WHERE order_id = ?::uuid", p.orderId());
        assertThat(fact.get("outcome")).isEqualTo("COMPLETED");
        assertThat(fact.get("quantity")).isEqualTo(2);
        assertThat(fact.get("approximate")).isEqualTo(false);                  // the real history was kept
        // the customer's link now shows no order
        mvc.perform(get("/api/sessions/" + p.sessionId() + "/track"))
                .andExpect(jsonPath("$.orders.length()").value(0));
    }

    @Test
    void theErasureIsLoggedUnderAKeyedHashAndNotTheSessionId() throws Exception {
        Placed p = place("Anna", "Latte");

        mvc.perform(delete("/api/sessions/" + p.sessionId() + "/data")).andExpect(status().isOk());

        var log = jdbc.queryForList("SELECT subject_type, subject_ref, performed_by FROM erasure_log WHERE subject_ref NOT LIKE '%-%'");
        assertThat(log).anySatisfy(r -> {
            assertThat(r.get("subject_type")).isEqualTo("CUSTOMER_SESSION");
            assertThat(r.get("performed_by")).isEqualTo("CUSTOMER");
            assertThat(r.get("subject_ref").toString()).hasSize(64).doesNotContain(p.sessionId());
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM erasure_log WHERE subject_ref LIKE ?", Integer.class, "%" + p.sessionId() + "%")).isZero();
    }

    @Test
    void erasingTwiceIsHarmless() throws Exception {
        Placed p = place("Anna", "Latte");
        subscribe(p.sessionId());

        mvc.perform(delete("/api/sessions/" + p.sessionId() + "/data")).andExpect(status().isOk());
        mvc.perform(delete("/api/sessions/" + p.sessionId() + "/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions").value(0));
    }

    @Test
    void anUnknownLinkIs404ForBoth() throws Exception {
        String unknown = UUID.randomUUID().toString();
        mvc.perform(get("/api/sessions/" + unknown + "/data")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/sessions/" + unknown + "/data")).andExpect(status().isNotFound());
    }

    @Test
    void afterARestoreTheLoggedErasuresAreRepeatedAndOthersAreLeftAlone() throws Exception {
        Placed forgotten = place("Anna Petrenko", "Latte for Anna");
        Placed kept = place("Ivan Kovalenko", "Espresso");
        subscribe(forgotten.sessionId());
        mvc.perform(delete("/api/sessions/" + forgotten.sessionId() + "/data")).andExpect(status().isOk());

        // a backup taken before the erasure is restored: the name and the subscription are back
        jdbc.update("UPDATE \"order\" SET customer_name = 'Anna Petrenko' WHERE id = ?::uuid", forgotten.orderId());
        subscribe(forgotten.sessionId());
        assertThat(jdbc.queryForObject("SELECT customer_name FROM \"order\" WHERE id = ?::uuid", String.class, forgotten.orderId()))
                .isEqualTo("Anna Petrenko");

        mvc.perform(post("/api/platform/erasures/replay").with(httpBasic("platform", "platform")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsErasedAgain").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        assertThat(jdbc.queryForObject("SELECT customer_name FROM \"order\" WHERE id = ?::uuid", String.class, forgotten.orderId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_subscription WHERE session_id = ?::uuid", Integer.class, forgotten.sessionId())).isZero();
        assertThat(jdbc.queryForObject("SELECT customer_name FROM \"order\" WHERE id = ?::uuid", String.class, kept.orderId()))
                .isEqualTo("Ivan Kovalenko");                                  // never asked to be forgotten
    }

    @Test
    void onlyThePlatformOperatorMayReplayErasures() throws Exception {
        mvc.perform(post("/api/platform/erasures/replay")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/platform/erasures/replay").with(httpBasic("admin", "admin"))).andExpect(status().isForbidden());
    }

    @Test
    void theOperatorCanRunTheRetentionNowAndOthersCannot() throws Exception {
        mvc.perform(post("/api/platform/retention/run").with(httpBasic("platform", "platform")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymize-orders").isNumber());
        mvc.perform(post("/api/platform/retention/run").with(httpBasic("admin", "admin"))).andExpect(status().isForbidden());
    }
}
