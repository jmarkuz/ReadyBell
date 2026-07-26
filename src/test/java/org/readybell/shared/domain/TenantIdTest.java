package org.readybell.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantId} value object.
 *
 * <p>Verifies that TenantId is immutable, properly validates input, and implements
 * equals/hashCode/toString correctly.
 */
class TenantIdTest {

    @Test
    void constructor_acceptsNonNullUuid() {
        UUID uuid = UUID.randomUUID();
        TenantId tenantId = new TenantId(uuid);

        assertEquals(uuid, tenantId.value());
    }

    @Test
    void constructor_rejectsNullUuid() {
        assertThrows(NullPointerException.class, () -> new TenantId(null));
    }

    @Test
    void equals_returnsTrueForSameUuid() {
        UUID uuid = UUID.randomUUID();
        TenantId id1 = new TenantId(uuid);
        TenantId id2 = new TenantId(uuid);

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void equals_returnsFalseForDifferentUuids() {
        TenantId id1 = new TenantId(UUID.randomUUID());
        TenantId id2 = new TenantId(UUID.randomUUID());

        assertNotEquals(id1, id2);
    }

    @Test
    void equals_returnsFalseForNull() {
        TenantId id = new TenantId(UUID.randomUUID());

        assertNotEquals(null, id);
    }

    @Test
    void equals_returnsFalseForDifferentType() {
        TenantId id = new TenantId(UUID.randomUUID());

        assertNotEquals("not-a-tenant-id", id);
    }

    @Test
    void toString_includesUuidRepresentation() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        TenantId id = new TenantId(uuid);

        String toString = id.toString();

        assertTrue(toString.contains("TenantId"));
        assertTrue(toString.contains(uuid.toString()));
    }

    @Test
    void recordMethods_workAsExpected() {
        UUID uuid = UUID.randomUUID();
        TenantId id = new TenantId(uuid);

        // Test component access
        assertEquals(uuid, id.value());
    }
}