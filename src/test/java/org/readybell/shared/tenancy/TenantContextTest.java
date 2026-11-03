package org.readybell.shared.tenancy;

import org.junit.jupiter.api.Test;
import org.readybell.shared.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantContext}.
 *
 * <p>Tests the mutable container that holds the resolved tenant for a request scope.
 */
class TenantContextTest {

    @Test
    void constructor_withoutParameters_createsEmptyContext() {
        TenantContext context = new TenantContext();

        assertFalse(context.isResolved());
        assertFalse(context.get().isPresent());
    }

    @Test
    void constructor_withTenantId_createsResolvedContext() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContext context = new TenantContext(tenantId);

        assertTrue(context.isResolved());
        assertEquals(tenantId, context.get().get());
    }

    @Test
    void constructor_withTenantId_acceptsNull() {
        // The constructor should accept null (creating an empty context)
        TenantContext context = new TenantContext(null);

        assertFalse(context.isResolved());
        assertFalse(context.get().isPresent());
    }

    @Test
    void set_resolvesContextToTenant() {
        TenantContext context = new TenantContext();
        TenantId tenantId = new TenantId(UUID.randomUUID());

        context.set(tenantId);

        assertTrue(context.isResolved());
        assertEquals(tenantId, context.get().get());
    }

    @Test
    void set_rejectsNullTenant() {
        TenantContext context = new TenantContext();

        assertThrows(NullPointerException.class, () -> context.set(null));
    }

    @Test
    void set_overwritesPreviousTenant() {
        TenantId tenant1 = new TenantId(UUID.randomUUID());
        TenantId tenant2 = new TenantId(UUID.randomUUID());
        TenantContext context = new TenantContext(tenant1);

        context.set(tenant2);

        assertEquals(tenant2, context.get().get());
    }

    @Test
    void set_onAlreadyResolvedContext_updatesTenant() {
        TenantId tenant1 = new TenantId(UUID.randomUUID());
        TenantId tenant2 = new TenantId(UUID.randomUUID());
        TenantContext context = new TenantContext(tenant1);

        context.set(tenant2);

        assertEquals(tenant2, context.get().get());
    }

    @Test
    void get_returnsEmptyOptionalWhenNotResolved() {
        TenantContext context = new TenantContext();

        Optional<TenantId> result = context.get();

        assertFalse(result.isPresent());
    }

    @Test
    void get_returnsTenantWhenResolved() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContext context = new TenantContext(tenantId);

        Optional<TenantId> result = context.get();

        assertTrue(result.isPresent());
        assertEquals(tenantId, result.get());
    }

    @Test
    void isResolved_returnsFalseForEmptyContext() {
        TenantContext context = new TenantContext();

        assertFalse(context.isResolved());
    }

    @Test
    void isResolved_returnsTrueForResolvedContext() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContext context = new TenantContext(tenantId);

        assertTrue(context.isResolved());
    }

    @Test
    void clear_resetsContextToEmpty() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContext context = new TenantContext(tenantId);

        context.clear();

        assertFalse(context.isResolved());
        assertFalse(context.get().isPresent());
    }

    @Test
    void clear_onEmptyContext_doesNothing() {
        TenantContext context = new TenantContext();

        assertDoesNotThrow(context::clear);
        assertFalse(context.isResolved());
    }

    @Test
    void clear_afterSet_returnsToEmpty() {
        TenantContext context = new TenantContext();
        TenantId tenantId = new TenantId(UUID.randomUUID());

        context.set(tenantId);
        assertTrue(context.isResolved());

        context.clear();
        assertFalse(context.isResolved());
    }

    @Test
    void roundTrip_setGetClearWorks() {
        TenantContext context = new TenantContext();
        TenantId tenantId = new TenantId(UUID.randomUUID());

        // Initially empty
        assertFalse(context.isResolved());

        // Set tenant
        context.set(tenantId);
        assertTrue(context.isResolved());
        assertEquals(tenantId, context.get().get());

        // Clear
        context.clear();
        assertFalse(context.isResolved());
        assertFalse(context.get().isPresent());

        // Can set again
        TenantId newTenantId = new TenantId(UUID.randomUUID());
        context.set(newTenantId);
        assertTrue(context.isResolved());
        assertEquals(newTenantId, context.get().get());
    }

    @Test
    void threadSafety_singleThreadOperations() {
        // TenantContext is not thread-safe by design (it's meant to be used within a single thread)
        // This test verifies basic single-thread behavior
        TenantContext context = new TenantContext();

        TenantId tenantId = new TenantId(UUID.randomUUID());
        context.set(tenantId);

        assertEquals(tenantId, context.get().get());
        assertTrue(context.isResolved());

        context.clear();

        assertFalse(context.isResolved());
    }
}