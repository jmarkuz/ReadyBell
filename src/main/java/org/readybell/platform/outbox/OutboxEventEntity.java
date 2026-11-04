package org.readybell.platform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One domain event waiting for (or done with) delivery. Deliberately not tenant-scoped: the relay is a system
 * component that processes every tenant's events; the event itself carries its tenant.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "type", nullable = false, updatable = false)
    String type;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt;

    @Column(name = "locked_until")
    Instant lockedUntil;

    @Column(name = "processed_at")
    Instant processedAt;

    @Column(name = "failed_at")
    Instant failedAt;

    @Column(name = "attempts", nullable = false)
    int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    String lastError;

    protected OutboxEventEntity() {}

    OutboxEventEntity(UUID id, UUID tenantId, String type, String payload, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
        this.payload = payload;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }
}
