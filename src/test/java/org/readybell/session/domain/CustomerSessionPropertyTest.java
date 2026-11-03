package org.readybell.session.domain;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests for CustomerSession identity and tenant assignment.
 * Uses repeated tests to simulate property-based testing.
 */
class CustomerSessionPropertyTest {

    private static final int REPETITIONS = 100;

    /**
     * Property 1: Session start assigns tenant correctly.
     * Repeated 100 times with random inputs.
     */
    @RepeatedTest(REPETITIONS)
    void sessionTenantMatchesInputTenant() {
        // Given random tenant and clock
        TenantId tenantId = new TenantId(UUID.randomUUID());
        long timestamp = (long) (Math.random() * 1_000_000_000L);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(timestamp), ZoneId.of("UTC"));
        
        // When starting a session
        CustomerSession session = CustomerSession.start(tenantId, clock);

        // Then the session's tenant equals the input tenant
        assertEquals(tenantId, session.getTenantId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertNotNull(session.getId());
        assertEquals(clock.instant(), session.getStartedAt());
    }

    /**
     * Property 2: Distinct identity across multiple session starts.
     * Repeated 100 times with random inputs.
     */
    @RepeatedTest(REPETITIONS)
    void sessionIdsAreDistinctAcrossStarts() {
        // Given random tenant-clock pairs
        TenantId tenantId1 = new TenantId(UUID.randomUUID());
        TenantId tenantId2 = new TenantId(UUID.randomUUID());
        long timestamp1 = (long) (Math.random() * 1_000_000_000L);
        long timestamp2 = (long) (Math.random() * 1_000_000_000L);
        Clock clock1 = Clock.fixed(Instant.ofEpochSecond(timestamp1), ZoneId.of("UTC"));
        Clock clock2 = Clock.fixed(Instant.ofEpochSecond(timestamp2), ZoneId.of("UTC"));
        
        // When starting two sessions
        CustomerSession session1 = CustomerSession.start(tenantId1, clock1);
        CustomerSession session2 = CustomerSession.start(tenantId2, clock2);

        // Then their IDs are distinct
        assertNotEquals(session1.getId(), session2.getId());

        // If tenants differ, tenantIds differ accordingly
        if (!tenantId1.equals(tenantId2)) {
            assertNotEquals(session1.getTenantId(), session2.getTenantId());
        }
    }

    /**
     * Property 3: Session identity distinctness within same tenant and time.
     * Repeated 100 times with random inputs.
     */
    @RepeatedTest(REPETITIONS)
    void sessionIdsAreDistinctEvenWithSameInputs() {
        // Given identical tenant and clock
        TenantId tenantId = new TenantId(UUID.randomUUID());
        long timestamp = (long) (Math.random() * 1_000_000_000L);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(timestamp), ZoneId.of("UTC"));
        
        // When starting two sessions
        CustomerSession session1 = CustomerSession.start(tenantId, clock);
        CustomerSession session2 = CustomerSession.start(tenantId, clock);

        // Then their IDs are distinct
        assertNotEquals(session1.getId(), session2.getId());
        // But other properties are equal
        assertEquals(session1.getTenantId(), session2.getTenantId());
        assertEquals(session1.getStartedAt(), session2.getStartedAt());
        assertEquals(session1.getStatus(), session2.getStatus());
    }

    /**
     * Property 4: Null safety - null tenant is rejected.
     */
    @Test
    void nullTenantIsRejected() {
        Clock clock = Clock.systemUTC();
        
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CustomerSession.start(null, clock));
        assertTrue(exception.getMessage().contains("tenantId"));
    }

    /**
     * Property 5: Null safety - null clock is rejected.
     */
    @Test
    void nullClockIsRejected() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CustomerSession.start(tenantId, null));
        assertTrue(exception.getMessage().contains("clock"));
    }

    /**
     * Property 6: Session started event is recorded.
     * Repeated 100 times with random inputs.
     */
    @RepeatedTest(REPETITIONS)
    void sessionStartedEventIsRecorded() {
        // When starting a session
        TenantId tenantId = new TenantId(UUID.randomUUID());
        long timestamp = (long) (Math.random() * 1_000_000_000L);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(timestamp), ZoneId.of("UTC"));
        CustomerSession session = CustomerSession.start(tenantId, clock);

        // Then one pending event exists
        assertEquals(1, session.getPendingEvents().size());
        // And it is a CustomerSessionStarted event
        assertTrue(session.getPendingEvents().get(0) instanceof CustomerSessionStarted);

        CustomerSessionStarted event = (CustomerSessionStarted) session.getPendingEvents().get(0);
        // With matching IDs and tenant
        assertNotNull(event.eventId());
        assertEquals(tenantId, event.tenantId());
        assertEquals(session.getId(), event.sessionId());
        assertEquals(clock.instant(), event.occurredAt());
    }

    /**
     * Property 7: Bulk distinctness - starting many sessions yields all-distinct IDs.
     */
    @Test
    void bulkSessionStartsProduceAllDistinctIds() {
        // Given a tenant and clock
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Clock clock = Clock.systemUTC();
        Set<CustomerSessionId> ids = new HashSet<>();
        int count = 100; // Test with 100 sessions
        
        // When starting multiple sessions
        for (int i = 0; i < count; i++) {
            CustomerSession session = CustomerSession.start(tenantId, clock);
            ids.add(session.getId());
        }

        // Then all IDs are distinct
        assertEquals(count, ids.size());
    }

    /**
     * Property 8: Credential-free creation - session can be created without any customer credentials.
     * This is implicit in all the other tests - we never provide credentials, only tenant and clock.
     */
    @Test
    void sessionCreationRequiresNoCredentials() {
        // The fact that we can create a session with just TenantId and Clock
        // demonstrates credential-free creation (Req 1.5)
        TenantId tenantId = new TenantId(UUID.randomUUID());
        Clock clock = Clock.systemUTC();
        
        // No credentials required - just tenant and clock
        CustomerSession session = CustomerSession.start(tenantId, clock);
        
        assertNotNull(session);
        assertEquals(tenantId, session.getTenantId());
        assertNotNull(session.getId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }
}