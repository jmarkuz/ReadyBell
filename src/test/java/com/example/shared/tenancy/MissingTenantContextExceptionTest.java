package com.example.shared.tenancy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MissingTenantContextException}.
 *
 * <p>Tests the exception that signals a tenant-scoped operation was attempted
 * with no resolved tenant in scope.
 */
class MissingTenantContextExceptionTest {

    @Test
    void defaultConstructor_setsDefaultMessage() {
        MissingTenantContextException exception = new MissingTenantContextException();

        assertEquals("tenant context missing", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void constructorWithMessage_setsCustomMessage() {
        String customMessage = "Custom error message";
        MissingTenantContextException exception = new MissingTenantContextException(customMessage);

        assertEquals(customMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void exceptionType_isRuntimeException() {
        MissingTenantContextException exception = new MissingTenantContextException();

        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void exception_canBeCaughtAsRuntimeException() {
        try {
            throw new MissingTenantContextException();
        } catch (RuntimeException e) {
            assertEquals("tenant context missing", e.getMessage());
        }
    }

    @Test
    void exception_inheritsRuntimeExceptionBehavior() {
        MissingTenantContextException exception = new MissingTenantContextException();

        // Verify it has all RuntimeException methods
        assertNotNull(exception.toString());
        assertNotNull(exception.getMessage());
        assertNull(exception.getCause());
        assertNotNull(exception.getStackTrace());
        assertTrue(exception.getStackTrace().length > 0);
    }

    @Test
    void exception_withCustomMessage_worksWithAssertThrows() {
        String customMessage = "No tenant found for request";

        MissingTenantContextException exception = assertThrows(
                MissingTenantContextException.class,
                () -> { throw new MissingTenantContextException(customMessage); }
        );

        assertEquals(customMessage, exception.getMessage());
    }
}