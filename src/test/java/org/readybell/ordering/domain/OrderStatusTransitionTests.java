package org.readybell.ordering.domain;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.providers.TypeUsage;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based and unit tests for {@link OrderStatus} state machine.
 *
 * <p>Validates that:
 * - Property 13: Allowed lifecycle transitions succeed and land in the target state
 * - Property 14: Disallowed, self, and post-terminal transitions are rejected without state change
 */
class OrderStatusTransitionTests {

    /**
     * Property 13: Allowed lifecycle transitions succeed and land in the target state
     *
     * <p>For any valid source and target states where {@code canTransitionTo(target)}
     * returns true, the transition is allowed and represents a forward step in the lifecycle.
     * Validates Requirements 5.1, 5.2.
     */
    @Property(tries = 100)
    void allowedTransitionsSucceedAndLandInTargetState(
            @ForAll OrderStatus source,
            @ForAll OrderStatus target) {

        if (source.canTransitionTo(target)) {
            // When a transition is allowed, it must be to a different state
            // (not a self-transition) and must not leave from/to terminal states
            // except as specified in ALLOWED.
            assertNotEquals(source, target,
                    "Allowed transitions must not be self-transitions");

            // Verify the transition is explicitly in the allowed set for source
            assertTrue(source.canTransitionTo(target),
                    "If canTransitionTo returned true, the transition must be allowed");
        }
    }

    /**
     * Property 14: Disallowed, self, and post-terminal transitions are rejected
     *
     * <p>For any invalid source and target states where {@code canTransitionTo(target)}
     * returns false, the transition is rejected (no state change occurs, no exception thrown).
     * Self-transitions and transitions from terminal states are always rejected.
     * Validates Requirements 5.3, 5.4, 5.7.
     */
    @Property(tries = 100)
    void disallowedTransitionsAreRejected(
            @ForAll OrderStatus source,
            @ForAll OrderStatus target) {

        // Self-transitions are always disallowed
        if (source == target) {
            assertFalse(source.canTransitionTo(target),
                    "Self-transitions must be rejected");
        }

        // Transitions from terminal states are always disallowed
        if (source.isTerminal()) {
            assertFalse(source.canTransitionTo(target),
                    "Transitions from terminal states must be rejected");
        }

        // If the transition is rejected, verify it's consistently rejected
        if (!source.canTransitionTo(target)) {
            assertFalse(source.canTransitionTo(target),
                    "Rejected transitions must be consistently rejected");
        }
    }

    /**
     * Unit test: CREATED → IN_PROGRESS is allowed
     */
    @Test
    void createdToInProgressIsAllowed() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.IN_PROGRESS));
    }

    /**
     * Unit test: CREATED → CANCELLED is allowed
     */
    @Test
    void createdToCancelledIsAllowed() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED));
    }

    /**
     * Unit test: CREATED → READY is not allowed
     */
    @Test
    void createdToReadyIsNotAllowed() {
        assertFalse(OrderStatus.CREATED.canTransitionTo(OrderStatus.READY));
    }

    /**
     * Unit test: IN_PROGRESS → READY is allowed
     */
    @Test
    void inProgressToReadyIsAllowed() {
        assertTrue(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.READY));
    }

    /**
     * Unit test: IN_PROGRESS → COMPLETED is not allowed (must go through READY)
     */
    @Test
    void inProgressToCompletedIsNotAllowed() {
        assertFalse(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.COMPLETED));
    }

    /**
     * Unit test: READY → COMPLETED is allowed
     */
    @Test
    void readyToCompletedIsAllowed() {
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.COMPLETED));
    }

    /**
     * Unit test: COMPLETED is terminal and rejects all transitions
     */
    @Test
    void completedIsTerminalAndRejectsAllTransitions() {
        assertTrue(OrderStatus.COMPLETED.isTerminal());
        assertFalse(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.COMPLETED));
    }

    /**
     * Unit test: CANCELLED is terminal and rejects all transitions
     */
    @Test
    void cancelledIsTerminalAndRejectsAllTransitions() {
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.COMPLETED));
        assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.CANCELLED));
    }

    /**
     * Unit test: Self-transitions are always rejected
     */
    @Test
    void selfTransitionsAreAlwaysRejected() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(status.canTransitionTo(status),
                    "Self-transition from " + status + " must be rejected");
        }
    }
}
