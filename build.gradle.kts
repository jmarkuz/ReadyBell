plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // PostgreSQL driver (runtime)
    runtimeOnly("org.postgresql:postgresql")

    // Web Push / VAPID (kept behind the NotificationSender port; ADR-005)
    implementation("nl.martijndwars:web-push:5.1.1")
    // BouncyCastle is required by web-push for EC cryptography (VAPID key operations)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    runtimeOnly("com.h2database:h2")

    // Embedded H2 for the test profile (application-test.yml uses H2 in PostgreSQL mode)
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.security:spring-security-test")

    // JUnit 5 (BOM already present via Spring Boot; keep explicit for clarity)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Property-based testing (jqwik) on the JUnit 5 platform
    testImplementation("net.jqwik:jqwik:1.9.2")
}

tasks.test {
    useJUnitPlatform()
}
