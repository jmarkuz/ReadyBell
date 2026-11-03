package org.readybell.session.domain;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for {@link CustomerSession} aggregate invariants.
 *
 * <p>**Validates: Requirements 1.1, 1.6**
 *
 * <p>**Property 1: Session start assigns tenant and distinct identity**
 *
 * <p>For any well-formed QR payload that resolves to an existing, active Restaurant,
 * starting a session creates an ACTIVE {@code CustomerSession} whose {@code TenantId}
 * equals that Restaurant's {@code TenantId}, and any two session starts produce
 * distinct {@code CustomerSessionId}s.
 */
@PropertyDefaults(tries = 100)
@Tag("restaurant-notification-platform")
@Tag("property-1-session-start-assigns-tenant-and-distinct-identity")
public class CustomerSessionProperties {

    // -------------------------------------------------------------------------
    // Arbitraries (test data generators)
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<TenantId> tenants() {
        return Arbitraries
                .create(() -> new TenantId(UUID.randomUUID()))
                .withoutEdgeCases();
    }

    @Provide
    Arbitrary<Clock> clocks() {
        return Arbitraries
                .longs()
                .between(0L, 1_000_000_000L)
                .map(seconds -> Clock.fixed(Instant.ofEpochSecond(seconds), ZoneId.of("UTC")));
    }

    // -------------------------------------------------------------------------
    // Property 1: Session start assigns tenant and distinct identity
    // -------------------------------------------------------------------------

    /**
     * **Property 1a: Tenant assignment**
     *
     * For any valid tenant and clock, a started session's tenant matches the input tenant.
     */
    @Property
    void sessionTenantMatchesInputTenant(
            @ForAll("tenants") TenantId tenantId,
            @ForAll("clocks") Clock clock
    ) {
        // When starting a session
        CustomerSession session = CustomerSession.start(tenantId, clock);

        // Then the session's tenant equals the input tenant
        assertEquals(tenantId, session.getTenantId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertNotNull(session.getId());
        assertEquals(clock.instant(), session.getStartedAt());
    }

    /**
     * **Property 1b: Distinct identity across multiple session starts**
     *
     * For any sequence of valid tenant-clock pairs, each started session receives
     * a distinct CustomerSessionId (collisions are prohibited by Req 1.1).
     */
    @Property
    void sessionIdsAreDistinctAcrossStarts(
            @ForAll("tenants") TenantId tenantId1,
            @ForAll("tenants") TenantId tenantId2,
            @ForAll("clocks") Clock clock1,
            @ForAll("clocks") Clock clock2
    ) {
        // When starting two sessions
        CustomerSession session1 = CustomerSession.start(tenantId1, clock1);
        CustomerSession session2 = CustomerSession.start(tenantId2, clock2);

        // Then their IDs are distinct
        assertNotEquals(session1.getId(), session2.getId());

        // Additionally, if the tenants differ, their tenantIds differ accordingly
        if (!tenantId1.equals(tenantId2)) {
            assertNotEquals(session1.getTenantId(), session2.getTenantId());
        }
    }

    /**
     * **Property 1c: Session identity distinctness within same tenant and time**
     *
     * Even with identical tenant and clock, two session starts produce distinct IDs.
     * This demonstrates that identity generation is independent of input.
     */
    @Property
    void sessionIdsAreDistinctEvenWithSameInputs(
            @ForAll("tenants") TenantId tenantId,
            @ForAll("clocks") Clock clock
    ) {
        // When starting two sessions
        CustomerSession session1 = CustomerSession.start(tenantId, clock);
        CustomerSession session2 = CustomerSession.start(tenantId, clock);

        // Then their IDs are distinct
        assertNotEquals(session1.getId(), session2.getId());
        // And their tenants are equal
        assertEquals(session1.getTenantId(), session2.getTenantId());
        // And their start times are equal
        assertEquals(session1.getStartedAt(), session2.getStartedAt());
    }

    /**
     * **Property 1d: Null safety — null tenant is rejected**
     *
     * The start factory rejects null tenant with NullPointerException.
     */
    @Property
    void nullTenantIsRejected(@ForAll("clocks") Clock clock) {
        // When attempting to start a session with null tenant
        // Then NullPointerException is thrown
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CustomerSession.start(null, clock));
        assertTrue(exception.getMessage().contains("tenantId"));
    }

    /**
     * **Property 1e: Null safety — null clock is rejected**
     *
     * The start factory rejects null clock with NullPointerException.
     */
    @Property
    void nullClockIsRejected(@ForAll("tenants") TenantId tenantId) {
        // When attempting to start a session with null clock
        // Then NullPointerException is thrown
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> CustomerSession.start(tenantId, null));
        assertTrue(exception.getMessage().contains("clock"));
    }

    /**
     * **Property 1f: Deterministic session creation for same inputs**
     *
     * This property tests that creating sessions with the same tenant and clock
     * (though they produce distinct IDs) otherwise results in sessions with
     * consistent properties.
     */
    @Property
    void sessionPropertiesAreConsistentForSameInputs(
            @ForAll("tenants") TenantId tenantId,
            @ForAll("clocks") Clock clock
    ) {
        // When starting multiple sessions
        CustomerSession session1 = CustomerSession.start(tenantId, clock);
        CustomerSession session2 = CustomerSession.start(tenantId, clock);

        // Then aside from IDs, sessions have identical properties
        assertEquals(session1.getTenantId(), session2.getTenantId());
        assertEquals(session1.getStatus(), session2.getStatus());
        assertEquals(session1.getStartedAt(), session2.getStartedAt());
        assertEquals(session1.isActive(), session2.isActive());
    }

    /**
     * **Property 1g: Session started event is recorded**
     *
     * Verifies that starting a session records a CustomerSessionStarted event
     * in the pending events list.
     */
    @Property
    void sessionStartedEventIsRecorded(
            @ForAll("tenants") TenantId tenantId,
            @ForAll("clocks") Clock clock
    ) {
        // When starting a session
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
     * **Property 1h: Bulk distinctness**
     *
     * Starting many sessions in a loop yields all-distinct IDs.
     */
    @Property
    void bulkSessionStartsProduceAllDistinctIds(
            @ForAll("tenants") TenantId tenantId,
            @ForAll("clocks") Clock clock
    ) {
        // Given a tenant and clock
        Set<CustomerSessionId> ids = new HashSet<>();
        int count = 10; // Fixed small count for property test
        
        // When starting multiple sessions
        for (int i = 0; i < count; i++) {
            CustomerSession session = CustomerSession.start(tenantId, clock);
            ids.add(session.getId());
        }

        // Then all IDs are distinct
        assertEquals(count, ids.size());
    }
}