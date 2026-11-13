package org.readybell.testsupport;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Points every Spring test context at the shared {@link SharedPostgres} and {@link SharedRedis} containers. Registered
 * in {@code META-INF/spring.factories}, so no test class has to opt in.
 */
public class TestContainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.datasource.url=" + SharedPostgres.jdbcUrl(),
                "spring.datasource.username=" + SharedPostgres.username(),
                "spring.datasource.password=" + SharedPostgres.password(),
                "spring.data.redis.host=" + SharedRedis.host(),
                "spring.data.redis.port=" + SharedRedis.port()
        ).applyTo(context);
    }
}
