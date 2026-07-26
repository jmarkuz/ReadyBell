package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class SimpleContextLoadTest {

    @Test
    void contextLoads() {
        // Simple test to verify Spring context loads
        assertThat(true).isTrue();
    }
}