package org.readybell.testsupport;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Points every Spring test context at the {@link SharedRedis} container. Registered in
 * {@code META-INF/spring.factories}, so no test class has to opt in.
 */
public class RedisContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.data.redis.host=" + SharedRedis.host(),
                "spring.data.redis.port=" + SharedRedis.port()
        ).applyTo(context);
    }
}
