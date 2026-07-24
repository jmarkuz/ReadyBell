package org.readybell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ReadyBell modular-monolith application.
 *
 * <p>A single deployable unit whose internal module boundaries mirror bounded
 * contexts (ADR-001). Spring Boot auto-configuration is rooted here so that all
 * {@code @Component}-annotated beans in sub-packages are discovered by default.
 *
 * <p>Stateless replicas are expected to run behind a load balancer with
 * liveness/readiness health probes enabled (Req 12.1, 12.4).
 */
@SpringBootApplication
public class ReadyBellApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadyBellApplication.class, args);
    }
}
