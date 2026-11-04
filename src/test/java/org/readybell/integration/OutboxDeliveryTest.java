package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.platform.outbox.OutboxRelay;
import org.readybell.platform.outbox.OutboxStore;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionRepository;
import org.readybell.subscription.domain.PushEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The OrderReady push is not lost when the process dies after the commit, is sent once even when the event is
 * delivered twice, and concurrent relays never take the same event.
 *
 * <p>The scheduled relay and the post-commit wake-up are switched off so the test plays the relay itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "readybell.outbox.enabled=false")
class OutboxDeliveryTest {

    private static final TenantId TENANT_A = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

    @Autowired private MockMvc mvc;
    @Autowired private CustomerSessionRepository sessions;
    @Autowired private DeviceSubscriptionRepository subscriptions;
    @Autowired private OutboxRelay relay;
    @Autowired private OutboxStore store;
    @Autowired private TransactionTemplate tx;
    @PersistenceContext private EntityManager em;
    @MockBean private NotificationSender sender;

    @BeforeEach
    void clean() {
        tx.executeWithoutResult(s -> em.createNativeQuery("DELETE FROM outbox_event").executeUpdate());
        when(sender.send(any(), any())).thenReturn(SendResult.ACCEPTED);
    }

    private String readyOrderOfASubscribedCustomer() throws Exception {
        var session = CustomerSession.start(TENANT_A, Clock.systemUTC());
        sessions.save(session);
        subscriptions.save(DeviceSubscription.register(TENANT_A, session.getId(),
                new PushEndpoint("https://push.example/" + UUID.randomUUID(), "p256dh", "auth")));
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerSessionId\":\"" + session.getId().value() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");
        for (String next : List.of("IN_PROGRESS", "READY")) {
            mvc.perform(patch("/api/orders/{id}/status", orderId).with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                    .andExpect(status().isOk());
        }
        return orderId;
    }

    @Test
    void eventSurvivesACrashAfterCommitAndIsPushedOnce() throws Exception {
        readyOrderOfASubscribedCustomer();

        // "crash": the order is READY and committed, the relay never ran
        verify(sender, never()).send(any(), any());
        assertThat(store.countPending()).isGreaterThanOrEqualTo(2);       // OrderStatusUpdated x3 + OrderReady

        // "restart": the relay finds the events in the database
        relay.drain();
        verify(sender, times(1)).send(any(), any());
        assertThat(store.countPending()).isZero();

        // nothing is delivered twice
        relay.drain();
        verify(sender, times(1)).send(any(), any());
    }

    @Test
    void replayedEventDoesNotPushAgain() throws Exception {
        readyOrderOfASubscribedCustomer();
        relay.drain();
        verify(sender, times(1)).send(any(), any());

        // the relay crashed after sending but before recording success: the same events come back
        tx.executeWithoutResult(s -> em.createNativeQuery(
                "UPDATE outbox_event SET processed_at = NULL, locked_until = NULL").executeUpdate());
        relay.drain();

        verify(sender, times(1)).send(any(), any());
        assertThat(store.countPending()).isZero();
    }

    @Test
    void failedBusinessTransactionLeavesNoEvent() throws Exception {
        // READY straight from CREATED is an invalid transition: 422, nothing committed
        var session = CustomerSession.start(TENANT_A, Clock.systemUTC());
        sessions.save(session);
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerSessionId\":\"" + session.getId().value() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long afterCreate = store.countPending();
        mvc.perform(patch("/api/orders/{id}/status", (String) JsonPath.read(body, "$.id")).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(store.countPending()).isEqualTo(afterCreate);
    }

    @Test
    void concurrentRelaysNeverClaimTheSameEvent() throws Exception {
        for (int i = 0; i < 40; i++) {
            readyEvent();
        }
        int threads = 4;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<OutboxStore.Claimed>>> results = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    var mine = new java.util.ArrayList<OutboxStore.Claimed>();
                    List<OutboxStore.Claimed> batch;
                    while (!(batch = store.claim(Instant.now(), Duration.ofMinutes(5), 5)).isEmpty()) {
                        mine.addAll(batch);
                    }
                    return mine;
                }));
            }
            start.countDown();
            var all = new java.util.ArrayList<UUID>();
            for (var f : results) {
                f.get().forEach(c -> all.add(c.id()));
            }
            assertThat(all).hasSize(40).doesNotHaveDuplicates();
        } finally {
            pool.shutdownNow();
        }
    }

    private void readyEvent() {
        var event = org.readybell.ordering.domain.OrderStatusUpdated.of(
                org.readybell.ordering.domain.OrderId.newId(), TENANT_A,
                new org.readybell.session.domain.CustomerSessionId(UUID.randomUUID()),
                org.readybell.ordering.domain.OrderStatus.CREATED,
                org.readybell.ordering.domain.OrderStatus.IN_PROGRESS, Instant.now());
        tx.executeWithoutResult(s -> store.append(event, "{}", Instant.now()));
    }
}
