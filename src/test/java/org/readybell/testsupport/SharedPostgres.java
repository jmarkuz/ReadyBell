package org.readybell.testsupport;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container per test JVM, started on first use and removed by Testcontainers when the JVM exits.
 * {@link TestContainersInitializer} points every Spring test context at it.
 *
 * <p>Spring caches a context per distinct test configuration and each one keeps its own connection pool, so a full run
 * easily holds more than PostgreSQL's default 100 connections ("too many clients already"): the limit is raised, and
 * durability is switched off because the data is thrown away anyway. Keep the image tag in line with docker-compose.yml.
 */
public final class SharedPostgres {

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("readybell")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres", "-c", "max_connections=300", "-c", "fsync=off");

    private SharedPostgres() {
    }

    public static synchronized void start() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
    }

    public static String jdbcUrl() {
        start();
        return CONTAINER.getJdbcUrl();
    }

    public static String username() {
        return CONTAINER.getUsername();
    }

    public static String password() {
        return CONTAINER.getPassword();
    }
}
