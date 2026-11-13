package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.adapter.in.web.sse.SseEmitterRegistry;
import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.testsupport.RecordingEmitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Creating an order through the use case (a real transaction) tells the restaurant's staff streams once it is committed. */
@SpringBootTest
@ActiveProfiles("test")
class NewOrderStreamTest {

    @Autowired private SseEmitterRegistry registry;
    @Autowired private ManageOrderUseCase manageOrders;
    @Autowired private CustomerSessionRepository sessions;
    @MockBean private NotificationSender notificationSender;

    @Test
    void staffStreamGetsOrderCreatedWithTheReceiptNumber() throws Exception {
        var tenant = new TenantId(UUID.randomUUID());
        var otherTenant = new TenantId(UUID.randomUUID());
        var staff = new RecordingEmitter();
        var otherStaff = new RecordingEmitter();
        registry.registerStaff(tenant, staff);
        registry.registerStaff(otherTenant, otherStaff);
        var session = CustomerSession.start(tenant, Clock.systemUTC());
        sessions.save(session);

        var order = manageOrders.createOrder(new CreateOrderCommand(tenant, session.getId()));

        long deadline = System.currentTimeMillis() + 5000;
        while (!staff.text().contains("order-created") && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(staff.text()).contains("\"id\":\"" + order.getId().value() + "\"")
                .contains("\"orderNumber\":\"#" + order.getOrderNumber() + "\"");
        assertThat(otherStaff.text()).doesNotContain("order-created");
    }
}
