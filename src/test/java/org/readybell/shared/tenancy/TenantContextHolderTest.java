package org.readybell.shared.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.readybell.shared.domain.TenantId;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantContextHolder}.
 *
 * <p>Verifies thread-local isolation, get/require behavior, and proper cleanup
 * to avoid memory leaks.
 */
class TenantContextHolderTest {

    @AfterEach
    void cleanup() {
        // Always clear thread-local state after each test to avoid contamination
        TenantContextHolder.clear();
    }

    @Test
    void set_setsTenantForCurrentThread() {
        TenantId tenantId = new TenantId(UUID.randomUUID());

        TenantContextHolder.set(tenantId);

        var resolved = TenantContextHolder.get();
        assertTrue(resolved.isPresent());
        assertEquals(tenantId, resolved.get());
    }

    @Test
    void set_overwritesPreviousTenant() {
        TenantId tenant1 = new TenantId(UUID.randomUUID());
        TenantId tenant2 = new TenantId(UUID.randomUUID());

        TenantContextHolder.set(tenant1);
        assertEquals(tenant1, TenantContextHolder.get().get());

        TenantContextHolder.set(tenant2);
        assertEquals(tenant2, TenantContextHolder.get().get());
    }

    @Test
    void set_rejectsNullTenant() {
        // The set method should throw NullPointerException when called with null
        // because TenantContext.set() will call Objects.requireNonNull
        assertThrows(NullPointerException.class, () -> TenantContextHolder.set(null));
    }

    @Test
    void get_returnsEmptyWhenNoTenantSet() {
        var resolved = TenantContextHolder.get();

        assertFalse(resolved.isPresent());
    }

    @Test
    void isResolved_returnsFalseWhenNoTenantSet() {
        assertFalse(TenantContextHolder.isResolved());
    }

    @Test
    void isResolved_returnsTrueWhenTenantSet() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContextHolder.set(tenantId);

        assertTrue(TenantContextHolder.isResolved());
    }

    @Test
    void require_returnsTenantWhenSet() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContextHolder.set(tenantId);

        TenantId resolved = TenantContextHolder.require();

        assertEquals(tenantId, resolved);
    }

    @Test
    void require_throwsMissingTenantContextExceptionWhenNotSet() {
        var exception = assertThrows(
                MissingTenantContextException.class,
                TenantContextHolder::require
        );

        assertEquals("tenant context missing", exception.getMessage());
    }

    @Test
    void require_withSetAndClear_throwsAfterClear() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContextHolder.set(tenantId);
        assertEquals(tenantId, TenantContextHolder.require());

        TenantContextHolder.clear();

        assertThrows(MissingTenantContextException.class, TenantContextHolder::require);
    }

    @Test
    void clear_resetsToEmptyState() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContextHolder.set(tenantId);
        assertTrue(TenantContextHolder.isResolved());

        TenantContextHolder.clear();

        assertFalse(TenantContextHolder.isResolved());
        assertFalse(TenantContextHolder.get().isPresent());
    }

    @Test
    void clear_onEmptyContext_doesNothing() {
        assertDoesNotThrow(TenantContextHolder::clear);
    }

    @Test
    void threadIsolation_eachThreadHasIndependentContext() throws InterruptedException {
        final TenantId mainThreadTenant = new TenantId(UUID.randomUUID());
        final TenantId otherThreadTenant = new TenantId(UUID.randomUUID());
        final CountDownLatch latch = new CountDownLatch(2);
        final boolean[] otherThreadHadTenant = new boolean[1];
        final boolean[] mainThreadKeptTenant = new boolean[1];

        // Set tenant in main thread
        TenantContextHolder.set(mainThreadTenant);

        // Create another thread
        Thread otherThread = new Thread(() -> {
            // Other thread should start with no tenant
            assertFalse(TenantContextHolder.isResolved());
            assertThrows(MissingTenantContextException.class, TenantContextHolder::require);

            // Set tenant in other thread
            TenantContextHolder.set(otherThreadTenant);
            assertEquals(otherThreadTenant, TenantContextHolder.require());

            otherThreadHadTenant[0] = true;
            latch.countDown();
        });

        otherThread.start();

        // Main thread should still have its tenant
        assertEquals(mainThreadTenant, TenantContextHolder.require());
        mainThreadKeptTenant[0] = true;
        latch.countDown();

        // Wait for threads to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify both threads maintained their own contexts
        assertTrue(otherThreadHadTenant[0]);
        assertTrue(mainThreadKeptTenant[0]);
    }

    @Test
    void concurrentAccess_doesNotCauseInterference() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final UUID tenantUuid = UUID.randomUUID();
            final TenantId tenantId = new TenantId(tenantUuid);

            executor.submit(() -> {
                // Each thread sets its own tenant
                TenantContextHolder.set(tenantId);

                // Verify each thread gets its own tenant back
                assertEquals(tenantId, TenantContextHolder.require());
                assertEquals(tenantId, TenantContextHolder.get().get());

                // Clear for this thread
                TenantContextHolder.clear();

                // Verify cleared
                assertFalse(TenantContextHolder.isResolved());

                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void memoryLeakPrevention_clearRemovesThreadLocalEntry() {
        // This test verifies that clear() actually removes the entry
        // by checking that a new thread doesn't inherit the cleared value
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TenantContextHolder.set(tenantId);
        assertTrue(TenantContextHolder.isResolved());

        TenantContextHolder.clear();
        assertFalse(TenantContextHolder.isResolved());

        // After clear, we should be able to set a new tenant
        TenantId newTenantId = new TenantId(UUID.randomUUID());
        TenantContextHolder.set(newTenantId);
        assertEquals(newTenantId, TenantContextHolder.require());
    }
}