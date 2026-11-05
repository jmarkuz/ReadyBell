package org.readybell.platform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    public OutboxEventSerializer outboxEventSerializer(ObjectMapper objectMapper) {
        return new OutboxEventSerializer(objectMapper);
    }
}
