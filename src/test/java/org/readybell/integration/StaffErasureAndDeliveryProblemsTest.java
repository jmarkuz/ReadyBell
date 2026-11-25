package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.platform.retention.RetentionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Removing a former employee's personal data, and the operator's view of what gave up. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffErasureAndDeliveryProblemsTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RetentionService retention;
    @MockBean private NotificationSender sender;

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Restaurant(String identifier, String owner, String tenant) {
        String basic() { return identifier + "/" + owner; }
    }

    private Restaurant restaurant() throws Exception {
        String s = suffix();
        String body = mvc.perform(post("/api/platform/restaurants").with(httpBasic("platform", "platform"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"erase-" + s + "\",\"name\":\"R\",\"adminUsername\":\"owner" + s
                                + "\",\"adminPassword\":\"correct-horse-battery\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Restaurant("erase-" + s, "owner" + s, JsonPath.read(body, "$.tenantId"));
    }

    private String createStaff(Restaurant r, String name) throws Exception {
        String body = mvc.perform(post("/api/admin/staff").with(httpBasic(r.basic(), "correct-horse-battery"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"role\":\"STAFF\",\"password\":\"a-fine-passphrase-7\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountId");
    }

    @Test
    void anAdminErasesAFormerEmployeeAtOnce() throws Exception {
        Restaurant r = restaurant();
        String id = createStaff(r, "anna" + suffix());

        mvc.perform(post("/api/admin/staff/{id}/erase", id).with(httpBasic(r.basic(), "correct-horse-battery")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.erased").value(true));

        var row = jdbc.queryForMap("SELECT username, password_hash, status FROM staff_account WHERE id = ?::uuid", id);
        assertThat(row.get("username").toString()).startsWith("deleted-");
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("status")).isEqualTo("DEACTIVATED");
    }

    @Test
    void anAdminCannotEraseThemselvesOrAnotherRestaurantsStaff() throws Exception {
        Restaurant mine = restaurant();
        Restaurant other = restaurant();
        String foreign = createStaff(other, "ivan" + suffix());
        String myOwnId = jdbc.queryForObject("SELECT id::text FROM staff_account WHERE tenant_id = ?::uuid AND username = ?",
                String.class, mine.tenant(), mine.owner());

        mvc.perform(post("/api/admin/staff/{id}/erase", myOwnId).with(httpBasic(mine.basic(), "correct-horse-battery")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/staff/{id}/erase", foreign).with(httpBasic(mine.basic(), "correct-horse-battery")))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT username FROM staff_account WHERE id = ?::uuid", String.class, foreign))
                .doesNotStartWith("deleted-");
    }

    @Test
    void theRetentionRunAnonymizesAccountsDeactivatedLongAgoOnly() throws Exception {
        Restaurant r = restaurant();
        String longAgo = createStaff(r, "old" + suffix());
        String recent = createStaff(r, "new" + suffix());
        String active = createStaff(r, "act" + suffix());
        jdbc.update("UPDATE staff_account SET status = 'DEACTIVATED', deactivated_at = ? WHERE id = ?::uuid",
                Timestamp.from(Instant.now().minus(Duration.ofDays(40))), longAgo);
        jdbc.update("UPDATE staff_account SET status = 'DEACTIVATED', deactivated_at = ? WHERE id = ?::uuid",
                Timestamp.from(Instant.now().minus(Duration.ofDays(3))), recent);

        retention.run(Instant.now(), java.util.UUID.fromString(r.tenant()));

        assertThat(jdbc.queryForObject("SELECT username FROM staff_account WHERE id = ?::uuid", String.class, longAgo)).startsWith("deleted-");
        assertThat(jdbc.queryForObject("SELECT username FROM staff_account WHERE id = ?::uuid", String.class, recent)).doesNotStartWith("deleted-");
        assertThat(jdbc.queryForObject("SELECT username FROM staff_account WHERE id = ?::uuid", String.class, active)).doesNotStartWith("deleted-");
    }

    @Test
    void deactivatingRecordsWhen() throws Exception {
        Restaurant r = restaurant();
        String id = createStaff(r, "bob" + suffix());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/staff/{id}", id)
                .with(httpBasic(r.basic(), "correct-horse-battery"))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT deactivated_at FROM staff_account WHERE id = ?::uuid", Timestamp.class, id)).isNotNull();
    }

    // ---- what gave up -------------------------------------------------------------------------------------------------

    private UUID parkedEvent() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event (id, tenant_id, type, payload, created_at, next_attempt_at, failed_at, attempts, last_error)
                VALUES (?, ?, 'org.readybell.ordering.domain.OrderReady', '{}', now(), now(), now(), 10, 'boom')""",
                id, UUID.randomUUID());
        return id;
    }

    private UUID failedPush(UUID subscription) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification (id, tenant_id, subscription_id, status, payload, created_at, attempts, title, url, last_error)
                VALUES (?, ?, ?, 'FAILED', 'Order #1 is ready for pickup!', now(), 5, 'Cafe', '/track/x', 'push service unavailable')""",
                id, UUID.randomUUID(), subscription);
        return id;
    }

    private UUID subscription() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO device_subscription (id, session_id, tenant_id, auth_key, endpoint_url, p256dh_key)
                VALUES (?, ?, ?, 'a', 'https://fcm.googleapis.com/fcm/send/x', 'k')""", id, UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    @Test
    void theOperatorSeesParkedEventsAndFailedPushesWithoutTheirContent() throws Exception {
        UUID event = parkedEvent();
        UUID push = failedPush(subscription());

        String json = mvc.perform(get("/api/platform/delivery/problems").with(httpBasic("platform", "platform")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parkedEvents[?(@.id=='" + event + "')].type").value("OrderReady"))
                .andExpect(jsonPath("$.parkedEvents[?(@.id=='" + event + "')].lastError").value("boom"))
                .andExpect(jsonPath("$.failedPushes[?(@.id=='" + push + "')].attempts").value(5))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("is ready for pickup");               // the text of a push is never listed
    }

    @Test
    void aParkedEventGoesBackInTheQueue() throws Exception {
        UUID event = parkedEvent();

        mvc.perform(post("/api/platform/delivery/events/{id}/retry", event).with(httpBasic("platform", "platform")))
                .andExpect(status().isOk());

        var row = jdbc.queryForMap("SELECT failed_at, attempts FROM outbox_event WHERE id = ?", event);
        assertThat(row.get("failed_at")).isNull();
        assertThat(row.get("attempts")).isEqualTo(0);
        mvc.perform(post("/api/platform/delivery/events/{id}/retry", event).with(httpBasic("platform", "platform")))
                .andExpect(status().isNotFound());                              // no longer parked
    }

    @Test
    void aFailedPushIsQueuedAgainAndDelivered() throws Exception {
        when(sender.send(any(), any())).thenReturn(SendResult.ACCEPTED);
        UUID push = failedPush(subscription());

        mvc.perform(post("/api/platform/delivery/pushes/{id}/retry", push).with(httpBasic("platform", "platform")))
                .andExpect(status().isOk());

        // the test profile delivers on the calling thread, so the worker has already handed it to the sender
        assertThat(jdbc.queryForObject("SELECT status FROM notification WHERE id = ?", String.class, push)).isEqualTo("SENT");
    }

    @Test
    void aPushWhoseTextWasRemovedOrWhoseDeviceIsGoneCannotBeRetried() throws Exception {
        UUID scrubbed = failedPush(subscription());
        jdbc.update("UPDATE notification SET payload = '' WHERE id = ?", scrubbed);
        UUID orphan = failedPush(UUID.randomUUID());                              // no such subscription

        mvc.perform(post("/api/platform/delivery/pushes/{id}/retry", scrubbed).with(httpBasic("platform", "platform")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/platform/delivery/pushes/{id}/retry", orphan).with(httpBasic("platform", "platform")))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOperatorSeesDeliveryProblems() throws Exception {
        mvc.perform(get("/api/platform/delivery/problems")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/delivery/problems").with(httpBasic("admin", "admin"))).andExpect(status().isForbidden());
    }
}
