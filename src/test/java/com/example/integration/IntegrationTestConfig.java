package com.example.integration;

import com.example.notification.domain.FakeNotificationSender;
import com.example.notification.domain.NotificationSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for integration tests.
 * 
 * <p>Provides a {@link FakeNotificationSender} bean that replaces the production
 * {@link NotificationSender} implementation, allowing tests to verify notification
 * delivery without sending actual Web Push messages.
 */
@TestConfiguration
public class IntegrationTestConfig {

    /**
     * Provides a {@link FakeNotificationSender} bean that will be used in place of
     * the production {@link NotificationSender} implementation.
     * 
     * @return a new {@link FakeNotificationSender} instance
     */
    @Bean
    @Primary
    public FakeNotificationSender fakeNotificationSender() {
        return new FakeNotificationSender();
    }
}