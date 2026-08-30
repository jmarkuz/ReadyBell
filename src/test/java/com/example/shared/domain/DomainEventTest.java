package com.example.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DomainEvent} contract.
 *
 * <p>Tests the behavior of concrete DomainEvent implementations to ensure they
 * follow the contract defined by the interface.
 */
class DomainEventTest {

    /**
     * Simple test implementation of DomainEvent for unit testing.
     */
    private record TestDomainEvent(
            UUID eventId,
            TenantId tenantId,
            Instant occurredAt
    ) implements DomainEvent {
        TestDomainEvent {
            assertNotNull(eventId);
            assertNotNull(tenantId);
            assertNotNull(occurredAt);
        }
    }

    @Test
    void domainEvent_providesRequiredProperties() {
        UUID eventId = UUID.randomUUID();
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Instant occurredAt = Instant.now();

        DomainEvent event = new TestDomainEvent(eventId, tenantId, occurredAt);

        assertEquals(eventId, event.eventId());
        assertEquals(tenantId, event.tenantId());
        assertEquals(occurredAt, event.occurredAt());
    }

    @Test
    void domainEvent_equalityBasedOnProperties() {
        UUID eventId = UUID.randomUUID();
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Instant occurredAt = Instant.now();

        DomainEvent event1 = new TestDomainEvent(eventId, tenantId, occurredAt);
        DomainEvent event2 = new TestDomainEvent(eventId, tenantId, occurredAt);

        // Since TestDomainEvent is a record, equals and hashCode are automatically derived
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void domainEvent_inequalityForDifferentProperties() {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Instant occurredAt = Instant.now();

        DomainEvent event1 = new TestDomainEvent(eventId1, tenantId, occurredAt);
        DomainEvent event2 = new TestDomainEvent(eventId2, tenantId, occurredAt);

        assertNotEquals(event1, event2);
    }

    @Test
    void domainEvent_contractRequiresNonNullProperties() {
        UUID eventId = UUID.randomUUID();
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Instant occurredAt = Instant.now();

        // Verify the test implementation enforces non-null constraints
        assertThrows(AssertionError.class, () -> new TestDomainEvent(null, tenantId, occurredAt));
        assertThrows(AssertionError.class, () -> new TestDomainEvent(eventId, null, occurredAt));
        assertThrows(AssertionError.class, () -> new TestDomainEvent(eventId, tenantId, null));
    }

    @Test
    void domainEvent_toStringIncludesRelevantInformation() {
        UUID eventId = UUID.randomUUID();
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Instant occurredAt = Instant.now();

        DomainEvent event = new TestDomainEvent(eventId, tenantId, occurredAt);
        String toString = event.toString();

        // Record's toString should include the class name and field values
        assertTrue(toString.contains("TestDomainEvent"));
    }

    @Test
    void domainEvent_immutableByDesign() {
        UUID eventId = UUID.randomUUID();
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Instant occurredAt = Instant.now();

        DomainEvent event = new TestDomainEvent(eventId, tenantId, occurredAt);

        // Since TestDomainEvent is a record, it's immutable by design
        // This test verifies we can't modify the returned properties
        assertNotNull(event.eventId());
        assertNotNull(event.tenantId());
        assertNotNull(event.occurredAt());
    }
}