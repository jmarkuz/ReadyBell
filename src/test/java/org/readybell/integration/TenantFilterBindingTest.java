package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationId;
import org.readybell.platform.persistence.TenantFilterInterceptor;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.MissingTenantContextException;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The tenant filter is bound by the interceptor around the adapters, with two real tenants in the database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantFilterBindingTest {

    private static final TenantId TENANT_A = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final TenantId TENANT_B = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));

    @Autowired private MockMvc mvc;
    @Autowired private OrderRepository orders;
    @Autowired private CustomerSessionRepository sessions;
    @Autowired private NotificationRepository notifications;
    @Autowired private TransactionTemplate tx;
    @Autowired private EntityManagerFactory emf;
    @MockBean private NotificationSender notificationSender;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private OrderId createOrderFor(TenantId tenant, String user) throws Exception {
        var session = CustomerSession.start(tenant, Clock.systemUTC());
        sessions.save(session);
        String body = mvc.perform(post("/api/orders").with(httpBasic(user, user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerSessionId\":\"" + session.getId().value() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new OrderId(UUID.fromString(JsonPath.read(body, "$.id")));
    }

    @Test
    void tenantScopedMethodsNeverReturnAnotherTenantsRows() throws Exception {
        OrderId orderOfB = createOrderFor(TENANT_B, "other");
        OrderId orderOfA = createOrderFor(TENANT_A, "admin");

        assertThat(orders.findByTenantAndId(TENANT_A, orderOfB)).isEmpty();
        assertThat(orders.findByTenantAndId(TENANT_B, orderOfB)).isPresent();
        assertThat(orders.findAllByTenant(TENANT_A)).noneMatch(o -> o.getId().equals(orderOfB));
        assertThat(orders.findAllByTenant(TENANT_B)).noneMatch(o -> o.getId().equals(orderOfA));
    }

    @Test
    void crossTenantMethodSeesEveryTenantEvenAfterAFilteredCallOnTheSameSession() throws Exception {
        OrderId orderOfB = createOrderFor(TENANT_B, "other");

        // Both calls join one transaction (one Hibernate session): the filter left by the first must not leak
        var found = tx.execute(status -> {
            var session = EntityManagerFactoryUtils.getTransactionalEntityManager(emf).unwrap(Session.class);
            assertThat(session.getEnabledFilter("tenantFilter")).isNull();

            assertThat(orders.findByTenantAndId(TENANT_A, orderOfB)).isEmpty();
            // the interceptor bound the filter to this very session, for the tenant argument
            assertThat(session.getEnabledFilter("tenantFilter")).isNotNull();
            assertThat(session.getEnabledFilter("tenantFilter").getFilterDefinition().getFilterName())
                    .isEqualTo("tenantFilter");

            var other = orders.findById(orderOfB);
            assertThat(session.getEnabledFilter("tenantFilter")).isNull();   // @CrossTenant switched it off
            return other;
        });
        assertThat(found).isPresent();
    }

    @Test
    void unmarkedMethodWithoutTenantArgumentOrContextFailsInsteadOfRunningUnfiltered() throws Throwable {
        var invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(Object.class.getMethod("toString"));
        when(invocation.getThis()).thenReturn(new Object());
        when(invocation.getArguments()).thenReturn(new Object[0]);
        var interceptor = new TenantFilterInterceptor(emf);

        assertThatThrownBy(() -> tx.execute(status -> {
            try {
                return interceptor.invoke(invocation);
            } catch (Throwable t) {
                sneakyThrow(t);
                return null;
            }
        })).isInstanceOf(MissingTenantContextException.class);
        verify(invocation, never()).proceed();

        // with a request context the same call is filtered for that tenant and proceeds
        TenantContextHolder.set(TENANT_A);
        tx.execute(status -> {
            try {
                return interceptor.invoke(invocation);
            } catch (Throwable t) {
                sneakyThrow(t);
                return null;
            }
        });
        verify(invocation).proceed();
    }

    @Test
    void tenantScopedNotificationLookupIsEmptyForAnUnknownId() {
        assertThat(notifications.findByTenantAndId(TENANT_A, new NotificationId(UUID.randomUUID()))).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
