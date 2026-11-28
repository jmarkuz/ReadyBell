package org.readybell.platform.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One run of one retention task, for the operator: when, how many rows, whether it only counted, and the error if it
 * failed. Holds no personal data. Written by {@link RetentionService} with native SQL; the entity makes the {@code dev}
 * profile's Hibernate-built schema contain the table, as Flyway has it elsewhere.
 */
@Entity
@Table(name = "retention_run")
class RetentionRunEntity {
    @Id
    UUID id;

    @Column(name = "task", nullable = false, length = 64)
    String task;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Column(name = "affected", nullable = false)
    int affected;

    @Column(name = "dry_run", nullable = false)
    boolean dryRun;

    @Column(name = "error", length = 255)
    String error;
}
