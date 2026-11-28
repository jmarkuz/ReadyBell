package org.readybell.platform.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Proof that a person's data was erased, without that data: the subject is a keyed hash, never the id itself. After a
 * restore from a backup the log is replayed so that what was forgotten stays forgotten. Written by
 * {@link CustomerDataService} with native SQL; the entity makes the {@code dev} schema contain the table.
 */
@Entity
@Table(name = "erasure_log", indexes = @Index(name = "idx_erasure_log_tenant", columnList = "tenant_id,performed_at"))
class ErasureLogEntity {
    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "subject_type", nullable = false, length = 32)
    String subjectType;

    @Column(name = "subject_ref", nullable = false, length = 128)
    String subjectRef;

    @Column(name = "scope", nullable = false, length = 64)
    String scope;

    @Column(name = "performed_at", nullable = false)
    Instant performedAt;

    @Column(name = "performed_by", nullable = false, length = 32)
    String performedBy;
}
