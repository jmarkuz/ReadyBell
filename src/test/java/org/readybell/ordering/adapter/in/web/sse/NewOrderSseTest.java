package org.readybell.ordering.adapter.in.web.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.readybell.ordering.domain.OrderCreated;
import org.readybell.ordering.domain.OrderId;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.testsupport.RecordingEmitter;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** A new order reaches the restaurant's staff streams, never a customer stream. */
class NewOrderSseTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RedisSseBridge> noRedis = mock(ObjectProvider.class);

    private final SseEmitterRegistry registry = new SseEmitterRegistry();
    private final OrderStatusFanout fanout = new OrderStatusFanout(registry, noRedis);
    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final CustomerSessionId session = new CustomerSessionId(UUID.randomUUID());

    private OrderCreated created(TenantId t, int number) {
        return OrderCreated.of(OrderId.newId(), t, session, Instant.now(), number);
    }

    @Test
    void staffOfTheRestaurantGetTheNewOrderWithItsNumber() {
        var staff = new RecordingEmitter();
        registry.registerStaff(tenant, staff);

        fanout.onOrderCreated(created(tenant, 12));

        assertThat(staff.text()).contains("\"event\":\"order-created\"").contains("\"orderNumber\":\"#12\"")
                .contains("\"status\":\"CREATED\"");
    }

    @Test
    void customersAndOtherRestaurantsDoNotGetIt() {
        var customer = new RecordingEmitter();
        var otherStaff = new RecordingEmitter();
        registry.registerCustomer(session, customer);
        registry.registerStaff(new TenantId(UUID.randomUUID()), otherStaff);

        fanout.onOrderCreated(created(tenant, 1));

        assertThat(customer.text()).doesNotContain("order-created").doesNotContain("\"orderId\"");
        assertThat(otherStaff.text()).doesNotContain("\"orderId\"");
    }

    @Test
    void statusUpdatesStillReachCustomerAndStaffWithoutTheNewFields() {
        var customer = new RecordingEmitter();
        var staff = new RecordingEmitter();
        registry.registerCustomer(session, customer);
        registry.registerStaff(tenant, staff);

        registry.deliver(new SseMessage(UUID.randomUUID(), session.value(), tenant.value(), "IN_PROGRESS"));

        for (var emitter : List.of(customer, staff)) {
            assertThat(emitter.text()).contains("\"status\":\"IN_PROGRESS\"")
                    .doesNotContain("\"event\"").doesNotContain("orderNumber");
        }
    }

    @Test
    void relayedMessagesFromAnOlderNodeAreStatusUpdates() throws Exception {
        var mapper = new ObjectMapper();
        var legacy = "{\"orderId\":\"" + UUID.randomUUID() + "\",\"sessionId\":\"" + session.value()
                + "\",\"tenantId\":\"" + tenant.value() + "\",\"status\":\"READY\"}";

        var message = mapper.readValue(legacy, SseMessage.class);
        var viaRedis = mapper.readValue(mapper.writeValueAsString(SseMessage.created(UUID.randomUUID(), session.value(),
                tenant.value(), 7)), SseMessage.class);

        assertThat(message.event()).isEqualTo(SseMessage.STATUS_UPDATED);
        assertThat(viaRedis.event()).isEqualTo(SseMessage.ORDER_CREATED);
        assertThat(viaRedis.orderNumber()).isEqualTo("#7");
    }
}
