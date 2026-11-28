package org.readybell.platform;

import org.readybell.notification.application.OrderReadyHandler;
import org.readybell.notification.application.PushDeliveryService;
import org.readybell.notification.domain.PushRetryPolicy;
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
 */
@Configuration
public class ApplicationConfig {
    @Bean
    public StartSessionUseCase startSessionUseCase(
            RestaurantRepository restaurantRepository,
            CustomerSessionRepository sessionRepository,
            DomainEventPublisher eventPublisher) {
        return new SessionService(restaurantRepository, sessionRepository, eventPublisher);
    }

    /**
     * Wires {@link ManageOrderService} to the {@link ManageOrderUseCase} port.
     *
     * <p>Uses {@link Clock#systemUTC()} in production; tests override this via the
     * constructor when exercising the service directly against fakes.
     */
    @Bean
    public ManageOrderUseCase manageOrderUseCase(
            CustomerSessionRepository sessionRepository,
            OrderRepository orderRepository,
            org.readybell.ordering.domain.OrderNumberGenerator orderNumberGenerator,
            org.readybell.ordering.domain.OrderTimelineRepository orderTimeline,
            DomainEventPublisher eventPublisher,
            Clock clock,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        // One transaction per use case: the order and its outbox events commit together
        return new org.readybell.platform.outbox.TransactionalManageOrderUseCase(
                new ManageOrderService(sessionRepository, orderRepository, orderNumberGenerator, orderTimeline, eventPublisher, clock),
                new org.springframework.transaction.support.TransactionTemplate(transactionManager));
    }

    /** The customer tracking page; the first open of a link also tells the staff that the customer is connected. */
    @Bean
    public TrackSessionUseCase trackSessionUseCase(CustomerSessionRepository sessionRepository,
                                                   OrderRepository orderRepository,
                                                   RestaurantRepository restaurantRepository,
                                                   DomainEventPublisher eventPublisher,
                                                   Clock clock) {
        return new TrackSessionService(sessionRepository, orderRepository, restaurantRepository, eventPublisher, clock);
    }

    /**
     * Which push endpoints the server may call: the known push services, plus any hosts listed in
     * {@code readybell.push.extra-allowed-hosts} (comma separated, {@code *.} for subdomains).
     */
    @Bean
    public org.readybell.subscription.domain.PushEndpointPolicy pushEndpointPolicy(
            @org.springframework.beans.factory.annotation.Value("${readybell.push.extra-allowed-hosts:}")
            java.util.List<String> extraAllowedHosts) {
        return new org.readybell.subscription.domain.PushEndpointPolicy(extraAllowedHosts);
    }

    /** Application clock; a single bean so ETAs, token expiry and tests share one time source. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ManageSubscriptionUseCase manageSubscriptionUseCase(
            CustomerSessionRepository sessionRepository,
            DeviceSubscriptionRepository subscriptionRepository,
            org.readybell.subscription.domain.PushEndpointPolicy pushEndpointPolicy) {
        return new ManageSubscriptionService(sessionRepository, subscriptionRepository, pushEndpointPolicy);
    }

    /**
     * Wires {@link OrderReadyHandler} as a plain bean so {@link SpringOrderReadyEventListener}
     * can receive it via constructor injection.
     *
     * <p>{@code ManageSubscriptionUseCase} is declared as a dependency so Spring resolves
     * it from the bean above, ensuring both the handler and the subscription service share
     * the same wired instance.
     */
    /**
     * Retry pauses after a transient push failure; empty means no retries (tests). The n-th failed attempt is followed
     * by the n-th pause, as a later {@code next_attempt_at} rather than a sleeping thread.
     */
    @Bean
    public PushRetryPolicy pushRetryPolicy(
            @Value("${readybell.push.retry-delays-ms:1000,5000,30000,120000}") List<Long> retryDelaysMs) {
        return new PushRetryPolicy(retryDelaysMs.stream().map(Duration::ofMillis).toList());
    }

    /** Makes one delivery attempt for a due notification and records the outcome (see its Javadoc). */
    @Bean
    public PushDeliveryService pushDeliveryService(
            NotificationRepository notificationRepository,
            DeviceSubscriptionRepository subscriptionRepository,
            NotificationSender notificationSender,
            org.readybell.platform.metrics.PushMetrics pushMetrics,
            DomainEventPublisher eventPublisher,
            ManageSubscriptionUseCase subscriptionUseCase,
            PushRetryPolicy retryPolicy,
            Clock clock) {
        return new PushDeliveryService(
                notificationRepository,
                subscriptionRepository,
                new org.readybell.platform.metrics.MeteredNotificationSender(notificationSender, pushMetrics),
                eventPublisher,
                subscriptionUseCase,
                retryPolicy,
                clock);
    }

    /** Queues a push per device when an order becomes READY and wakes the delivery worker. */
    @Bean
    public OrderReadyHandler orderReadyHandler(
            DeviceSubscriptionRepository subscriptionRepository,
            NotificationRepository notificationRepository,
            RestaurantRepository restaurantRepository,
            OrderRepository orderRepository,
            org.readybell.notification.application.DeliveryTrigger deliveryTrigger,
            @Value("${readybell.push.deadline:10m}") Duration deadline,
            Clock clock) {
        return new OrderReadyHandler(
                subscriptionRepository,
                notificationRepository,
                restaurantRepository,
                orderRepository,
                deliveryTrigger,
                deadline,
                clock);
    }

    @Bean
    public RestaurantAdminUseCase restaurantAdminUseCase(
            RestaurantRepository restaurantRepository,
            StaffAccountRepository staffRepository) {
        return new RestaurantAdminService(restaurantRepository, staffRepository);
    }

    @Bean
    public PlatformAdminUseCase platformAdminUseCase(RestaurantRepository restaurantRepository,
                                                      StaffAccountRepository staffRepository) {
        return new PlatformAdminService(restaurantRepository, staffRepository);
    }
}
