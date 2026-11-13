package org.readybell.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One Redis container per test JVM, started on first use and removed by Testcontainers when the JVM exits.
 * Spring test contexts are pointed at it by {@link RedisContainerInitializer}; plain unit tests read the address here.
 * Keep the image tag in line with docker-compose.yml.
 */
public final class SharedRedis {

    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private SharedRedis() {
    }

    public static synchronized void start() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
    }

    public static String host() {
        start();
        return CONTAINER.getHost();
    }

    public static int port() {
        start();
        return CONTAINER.getMappedPort(6379);
    }
}
