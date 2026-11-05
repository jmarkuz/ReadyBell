package org.readybell.platform;

import org.readybell.notification.application.OrderReadyHandler;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.application.ManageOrderService;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.application.TrackSessionService;
import org.readybell.ordering.application.TrackSessionUseCase;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.platformadmin.application.PlatformAdminService;
import org.readybell.platformadmin.application.PlatformAdminUseCase;
import org.readybell.restaurant.application.RestaurantAdminService;
import org.readybell.restaurant.application.RestaurantAdminUseCase;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.restaurant.domain.StaffAccountRepository;
import org.readybell.session.application.SessionService;
import org.readybell.session.application.StartSessionUseCase;
import org.readybell.shared.tenancy.TenantContextHolderProvider;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.DomainEventPublisher;
import org.readybell.subscription.application.ManageSubscriptionService;
import org.readybell.subscription.application.ManageSubscriptionUseCase;
import org.readybell.subscription.domain.DeviceSubscriptionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.time.Duration;

/**
 * Composition root — wires the hexagonal ports to their production adapters.
 *
 * <p>All application services are plain Java (no Spring annotations); they receive their
 * dependencies through constructor injection here. Production adapters
 * ({@link SpringDomainEventPublisherAdapter},
 * {@link org.readybell.notification.adapter.out.push.VapidNotificationSenderAdapter},
 * {@link TenantContextHolderProvider}, and all
 * {@code @Repository}-annotated JPA adapters) are discovered automatically by Spring's
 * component scan and injected by type.
 *
 * <p>Requirements addressed: 7.1, 8.1, 9.1, 10.4, 11.1.
 */
@Configuration
public class ApplicationConfig {

    // -------------------------------------------------------------------------
    // Session bounded context
    // -------------------------------------------------------------------------

    /**
     * Wires {@link SessionService} to the {@link StartSessionUseCase} port.
     *
     * @param restaurantRepository  JPA adapter auto-discovered by Spring Data
     * @param sessionRepository     JPA adapter auto-discovered by Spring Data
     * @param eventPublisher        {@link SpringDomainEventPublisherAdapter} (@Component)
     * @return the application service as the {@link StartSessionUseCase} port
     */
    @Bean
    public StartSessionUseCase startSessionUseCase(
            RestaurantRepository restaurantRepository,
            CustomerSessionRepository sessionRepository,
            DomainEventPublisher eventPublisher) {
        return new SessionService(restaurantRepository, sessionRepository, eventPublisher);
    }

    // -------------------------------------------------------------------------
    // Ordering bounded context
    // -------------------------------------------------------------------------

    /**
     * Wires {@link ManageOrderService} to the {@link ManageOrderUseCase} port.
     *
     * <p>Uses {@link Clock#systemUTC()} in production; tests override this via the
     * constructor when exercising the service directly against fakes.
     *
     * @param sessionRepository JPA adapter auto-discovered by Spring Data
     * @param orderRepository   JPA adapter auto-discovered by Spring Data
     * @param eventPublisher    {@link SpringDomainEventPublisherAdapter} (@Component)
     * @return the application service as the {@link ManageOrderUseCase} port
     */
    @Bean
    public ManageOrderUseCase manageOrderUseCase(
            CustomerSessionRepository sessionRepository,
            OrderRepository orderRepository,
            org.readybell.ordering.domain.OrderNumberGenerator orderNumberGenerator,
            DomainEventPublisher eventPublisher,
            Clock clock,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        // One transaction per use case: the order and its outbox events commit together
        return new org.readybell.platform.outbox.TransactionalManageOrderUseCase(
                new ManageOrderService(sessionRepository, orderRepository, orderNumberGenerator, eventPublisher, clock),
                new org.springframework.transaction.support.TransactionTemplate(transactionManager));
    }

    /** Read side of the customer tracking page. */
    @Bean
    public TrackSessionUseCase trackSessionUseCase(CustomerSessionRepository sessionRepository,
                                                   OrderRepository orderRepository,
                                                   RestaurantRepository restaurantRepository) {
        return new TrackSessionService(sessionRepository, orderRepository, restaurantRepository);
    }

    /** Application clock; a single bean so ETAs, token expiry and tests share one time source. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // -------------------------------------------------------------------------
    // Subscription bounded context
    // -------------------------------------------------------------------------

    /**
     * Wires {@link ManageSubscriptionService} to the {@link ManageSubscriptionUseCase} port.
     *
     * @param sessionRepository      JPA adapter auto-discovered by Spring Data
     * @param subscriptionRepository JPA adapter auto-discovered by Spring Data
     * @return the application service as the {@link ManageSubscriptionUseCase} port
     */
    @Bean
    public ManageSubscriptionUseCase manageSubscriptionUseCase(
            CustomerSessionRepository sessionRepository,
            DeviceSubscriptionRepository subscriptionRepository) {
        return new ManageSubscriptionService(sessionRepository, subscriptionRepository);
    }

    // -------------------------------------------------------------------------
    // Notification bounded context
    // -------------------------------------------------------------------------

    /**
     * Wires {@link OrderReadyHandler} as a plain bean so {@link SpringOrderReadyEventListener}
     * can receive it via constructor injection.
     *
     * <p>{@code ManageSubscriptionUseCase} is declared as a dependency so Spring resolves
     * it from the bean above, ensuring both the handler and the subscription service share
     * the same wired instance.
     *
     * @param subscriptionRepository JPA adapter auto-discovered by Spring Data
     * @param notificationRepository JPA adapter auto-discovered by Spring Data
     * @param notificationSender     {@link org.readybell.notification.adapter.out.push.VapidNotificationSenderAdapter} (@Component)
     * @param eventPublisher         {@link SpringDomainEventPublisherAdapter} (@Component)
     * @param subscriptionUseCase    the {@link ManageSubscriptionUseCase} bean defined above
     * @return the wired handler
     */
    @Bean
    public OrderReadyHandler orderReadyHandler(
            DeviceSubscriptionRepository subscriptionRepository,
            NotificationRepository notificationRepository,
            NotificationSender notificationSender,
            org.readybell.platform.metrics.PushMetrics pushMetrics,
            DomainEventPublisher eventPublisher,
            ManageSubscriptionUseCase subscriptionUseCase,
            RestaurantRepository restaurantRepository,
            OrderRepository orderRepository,
            @Value("${readybell.push.retry-delays-ms:500,2000,5000}")
            List<Long> retryDelaysMs) {
        return new OrderReadyHandler(
                subscriptionRepository,
                notificationRepository,
                new org.readybell.platform.metrics.MeteredNotificationSender(notificationSender, pushMetrics),
                eventPublisher,
                subscriptionUseCase,
                restaurantRepository,
                orderRepository,
                retryDelaysMs.stream().map(Duration::ofMillis).toList());
    }

    // -------------------------------------------------------------------------
    // Restaurant administration bounded context
    // -------------------------------------------------------------------------

    /**
     * Wires {@link RestaurantAdminService} to the {@link RestaurantAdminUseCase} port.
     *
     * @param restaurantRepository JPA adapter auto-discovered by Spring Data
     * @param staffRepository      JPA adapter auto-discovered by Spring Data
     * @return the application service as the {@link RestaurantAdminUseCase} port
     */
    @Bean
    public RestaurantAdminUseCase restaurantAdminUseCase(
            RestaurantRepository restaurantRepository,
            StaffAccountRepository staffRepository) {
        return new RestaurantAdminService(restaurantRepository, staffRepository);
    }

    // -------------------------------------------------------------------------
    // Platform administration bounded context
    // -------------------------------------------------------------------------

    /**
     * Wires {@link PlatformAdminService} to the {@link PlatformAdminUseCase} port.
     *
     * @param restaurantRepository JPA adapter auto-discovered by Spring Data
     * @return the application service as the {@link PlatformAdminUseCase} port
     */
    @Bean
    public PlatformAdminUseCase platformAdminUseCase(RestaurantRepository restaurantRepository,
                                                      StaffAccountRepository staffRepository) {
        return new PlatformAdminService(restaurantRepository, staffRepository);
    }
}
