/**
 * Unit tests for error handling and RFC 7807 parser
 * 
 * Tests:
 * - ProblemDetail parsing from various input formats
 * - Error context extraction from URN type
 * - User-friendly message mapping
 * - Error classification (auth, validation, server, etc.)
 * - Retryability determination
 * - Severity level mapping
 * - Edge cases and error handling
 */

import {
  parseProblemDetail,
  extractProblemDetail,
  createParsedError,
  extractErrorContext,
  mapErrorToUserMessage,
  mapStatusToErrorType,
  isRetryable,
  mapStatusToSeverity,
  handleErrorResponse,
  ApiError,
  ERROR_MESSAGES,
  type ProblemDetail,
  type ParsedError,
  type ErrorContext,
} from './errors';

describe('Error Handling - RFC 7807 Parser', () => {
  describe('extractProblemDetail', () => {
    it('should extract ProblemDetail from already-parsed object', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:auth:invalid-token',
        title: 'Invalid Token',
        status: 401,
        detail: 'Token has expired',
        instance: '/api/orders',
      };

      const result = extractProblemDetail(problemDetail);
      expect(result).toEqual(problemDetail);
    });

    it('should handle Response object with status', () => {
      const mockResponse = {
        status: 404,
        statusText: 'Not Found',
        url: '/api/orders/123',
      } as Response;

      const result = extractProblemDetail(mockResponse);
      expect(result.status).toBe(404);
      expect(result.type).toContain('http-404');
      expect(result.instance).toBe('/api/orders/123');
    });

    it('should return generic error for unknown input', () => {
      const result = extractProblemDetail({ foo: 'bar' });
      expect(result.type).toContain('unknown');
      expect(result.status).toBe(0);
    });

    it('should handle null or undefined input', () => {
      const result1 = extractProblemDetail(null);
      const result2 = extractProblemDetail(undefined);
      
      expect(result1.type).toContain('unknown');
      expect(result2.type).toContain('unknown');
    });
  });

  describe('extractErrorContext', () => {
    it('should extract context from valid URN format', () => {
      const urn = 'urn:readybell:auth:invalid-token';
      const context = extractErrorContext(urn);
      expect(context).toBe('auth:invalid-token');
    });

    it('should extract orders context', () => {
      const urn = 'urn:readybell:orders:not-found';
      const context = extractErrorContext(urn);
      expect(context).toBe('orders:not-found');
    });

    it('should extract validation context', () => {
      const urn = 'urn:readybell:validation:invalid-request';
      const context = extractErrorContext(urn);
      expect(context).toBe('validation:invalid-request');
    });

    it('should extract admin context', () => {
      const urn = 'urn:readybell:admin:staff-not-found';
      const context = extractErrorContext(urn);
      expect(context).toBe('admin:staff-not-found');
    });

    it('should return undefined for unknown URN format', () => {
      expect(extractErrorContext('invalid-urn')).toBeUndefined();
      expect(extractErrorContext('urn:unknown:type')).toBeUndefined();
      expect(extractErrorContext('')).toBeUndefined();
    });

    it('should return undefined for unknown error context', () => {
      const urn = 'urn:readybell:unknown:unknown-error';
      const context = extractErrorContext(urn);
      expect(context).toBeUndefined();
    });
  });

  describe('mapErrorToUserMessage', () => {
    it('should map auth:invalid-token to user message when no detail', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:auth:invalid-token',
        title: 'Invalid Token',
        status: 401,
        detail: 'x', // Too short to use
      };

      const message = mapErrorToUserMessage(problemDetail, 'auth:invalid-token');
      expect(message).toBe('Your session has expired. Please log in again.');
    });

    it('should map orders:not-found to user message when no detail', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:orders:not-found',
        title: 'Not Found',
        status: 404,
        detail: 'x', // Too short to use
      };

      const message = mapErrorToUserMessage(problemDetail, 'orders:not-found');
      expect(message).toBe('Order not found. It may have been deleted.');
    });

    it('should prefer detail field when available and user-friendly for auth errors', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:auth:invalid-token',
        title: 'Invalid Token',
        status: 401,
        detail: 'Your access token has expired due to inactivity',
      };

      const message = mapErrorToUserMessage(problemDetail, 'auth:invalid-token');
      // Detail field takes priority over ERROR_MESSAGES mapping
      expect(message).toBe('Your access token has expired due to inactivity');
    });

    it('should use generic message when detail contains technical info', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:server:database-error',
        title: 'Database Error',
        status: 500,
        detail: 'SQLException: Connection timeout',
      };

      const message = mapErrorToUserMessage(problemDetail);
      expect(message).not.toContain('SQLException');
      expect(message).toContain('server encountered an error');
    });

    it('should use detail field when it contains a user-friendly message', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:validation:invalid-request',
        title: 'Validation Error',
        status: 400,
        detail: 'Field "email" must be a valid email address',
      };

      const message = mapErrorToUserMessage(problemDetail);
      // Should use the detail field since it's user-friendly
      expect(message).toBe('Field "email" must be a valid email address');
    });

    it('should map status-based messages when detail is short', () => {
      const testCases = [
        { status: 400, expectedContains: 'invalid data' },
        { status: 401, expectedContains: 'session has expired' },
        { status: 403, expectedContains: 'permission' },
        { status: 404, expectedContains: 'not found' },
        { status: 409, expectedContains: 'modified by another user' },
        { status: 500, expectedContains: 'server encountered an error' },
      ];

      testCases.forEach(({ status, expectedContains }) => {
        const problemDetail: ProblemDetail = {
          type: `urn:readybell:server:error-${status}`,
          title: `Error ${status}`,
          status,
          detail: 'x', // Too short to use
        };

        const message = mapErrorToUserMessage(problemDetail);
        expect(message.toLowerCase()).toContain(expectedContains.toLowerCase());
      });
    });
  });

  describe('mapStatusToErrorType', () => {
    it('should map auth status codes to auth type', () => {
      expect(mapStatusToErrorType(401)).toBe('auth');
      expect(mapStatusToErrorType(403)).toBe('auth');
    });

    it('should map validation status codes to validation type', () => {
      expect(mapStatusToErrorType(400)).toBe('validation');
      expect(mapStatusToErrorType(422)).toBe('validation');
    });

    it('should map conflict status code to conflict type', () => {
      expect(mapStatusToErrorType(409)).toBe('conflict');
    });

    it('should map server error status codes to server type', () => {
      expect(mapStatusToErrorType(500)).toBe('server');
      expect(mapStatusToErrorType(502)).toBe('server');
      expect(mapStatusToErrorType(503)).toBe('server');
      expect(mapStatusToErrorType(504)).toBe('server');
    });

    it('should map network error codes to network type', () => {
      expect(mapStatusToErrorType(0)).toBe('network');
      expect(mapStatusToErrorType(408)).toBe('network');
    });

    it('should map unknown codes to unknown type', () => {
      expect(mapStatusToErrorType(418)).toBe('unknown'); // I'm a teapot (4xx, not covered)
      expect(mapStatusToErrorType(999)).toBe('unknown'); // Very large code
    });
  });

  describe('isRetryable', () => {
    it('should mark 5xx errors as retryable', () => {
      expect(isRetryable(500)).toBe(true);
      expect(isRetryable(502)).toBe(true);
      expect(isRetryable(503)).toBe(true);
      expect(isRetryable(504)).toBe(true);
    });

    it('should mark 408 and 429 as retryable', () => {
      expect(isRetryable(408)).toBe(true);
      expect(isRetryable(429)).toBe(true);
    });

    it('should mark network errors as retryable', () => {
      expect(isRetryable(0)).toBe(true);
    });

    it('should mark 4xx errors as not retryable', () => {
      expect(isRetryable(400)).toBe(false);
      expect(isRetryable(401)).toBe(false);
      expect(isRetryable(403)).toBe(false);
      expect(isRetryable(404)).toBe(false);
      expect(isRetryable(422)).toBe(false);
    });

    it('should mark 3xx and 2xx as not retryable', () => {
      expect(isRetryable(200)).toBe(false);
      expect(isRetryable(301)).toBe(false);
    });
  });

  describe('mapStatusToSeverity', () => {
    it('should map auth errors to critical', () => {
      expect(mapStatusToSeverity(401)).toBe('critical');
      expect(mapStatusToSeverity(403)).toBe('critical');
    });

    it('should map other 4xx to warning', () => {
      expect(mapStatusToSeverity(400)).toBe('warning');
      expect(mapStatusToSeverity(404)).toBe('warning');
      expect(mapStatusToSeverity(409)).toBe('warning');
      expect(mapStatusToSeverity(422)).toBe('warning');
    });

    it('should map 5xx to error', () => {
      expect(mapStatusToSeverity(500)).toBe('error');
      expect(mapStatusToSeverity(502)).toBe('error');
      expect(mapStatusToSeverity(503)).toBe('error');
      expect(mapStatusToSeverity(504)).toBe('error');
    });

    it('should map network errors to error', () => {
      expect(mapStatusToSeverity(0)).toBe('error');
      expect(mapStatusToSeverity(408)).toBe('warning'); // Timeout is warning (user can retry)
    });
  });

  describe('createParsedError', () => {
    it('should create complete ParsedError from ProblemDetail', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:orders:concurrent-modification',
        title: 'Concurrent Modification',
        status: 409,
        detail: 'x', // Too short to use
        instance: '/api/orders/123',
      };

      const parsed = createParsedError(problemDetail);

      expect(parsed.problemDetail).toEqual(problemDetail);
      expect(parsed.statusCode).toBe(409);
      expect(parsed.errorType).toBe('conflict');
      expect(parsed.retryable).toBe(false);
      expect(parsed.severity).toBe('warning');
      expect(parsed.errorContext).toBe('orders:concurrent-modification');
      // Should use user-friendly message from ERROR_MESSAGES mapping
      expect(parsed.userMessage).toBe('Order was updated by another user. Refreshing order details...');
      expect(parsed.timestamp).toBeInstanceOf(Date);
    });

    it('should handle 401 Unauthorized', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:auth:invalid-token',
        title: 'Invalid Token',
        status: 401,
        detail: 'x', // Too short to use
      };

      const parsed = createParsedError(problemDetail);

      expect(parsed.errorType).toBe('auth');
      expect(parsed.severity).toBe('critical');
      expect(parsed.userMessage).toBe('Your session has expired. Please log in again.');
    });

    it('should handle 500 Server Error', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:server:internal-error',
        title: 'Internal Server Error',
        status: 500,
        detail: 'Unexpected error occurred',
      };

      const parsed = createParsedError(problemDetail);

      expect(parsed.errorType).toBe('server');
      expect(parsed.severity).toBe('error');
      expect(parsed.retryable).toBe(true);
    });
  });

  describe('parseProblemDetail', () => {
    it('should parse Response object', () => {
      const mockResponse = {
        status: 401,
        statusText: 'Unauthorized',
        url: '/api/orders',
      } as Response;

      const parsed = parseProblemDetail(mockResponse);

      expect(parsed.statusCode).toBe(401);
      expect(parsed.errorType).toBe('auth');
    });

    it('should parse raw ProblemDetail object', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:validation:invalid-request',
        title: 'Validation Error',
        status: 400,
        detail: 'Email is required',
      };

      const parsed = parseProblemDetail(problemDetail);

      expect(parsed.statusCode).toBe(400);
      expect(parsed.errorContext).toBe('validation:invalid-request');
    });
  });

  describe('handleErrorResponse', () => {
    it('should handle Response object', () => {
      const mockResponse = {
        status: 404,
        statusText: 'Not Found',
        url: '/api/orders/999',
      } as Response;

      const parsed = handleErrorResponse(mockResponse);

      expect(parsed.statusCode).toBe(404);
      expect(parsed.errorType).toBe('unknown');
      expect(parsed.userMessage.toLowerCase()).toContain('not found');
    });

    it('should handle Error object', () => {
      const error = new Error('Network timeout');

      const parsed = handleErrorResponse(error);

      expect(parsed.statusCode).toBe(0);
      expect(parsed.problemDetail.detail).toBe('Network timeout');
    });

    it('should handle ProblemDetail object', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:admin:staff-not-found',
        title: 'Not Found',
        status: 404,
        detail: 'Staff account not found',
      };

      const parsed = handleErrorResponse(problemDetail);

      expect(parsed.statusCode).toBe(404);
      expect(parsed.errorContext).toBe('admin:staff-not-found');
    });

    it('should handle null or undefined', () => {
      const parsed1 = handleErrorResponse(null);
      const parsed2 = handleErrorResponse(undefined);

      expect(parsed1.statusCode).toBe(0);
      expect(parsed2.statusCode).toBe(0);
    });
  });

  describe('ApiError exception', () => {
    it('should create ApiError with parsed error', () => {
      const problemDetail: ProblemDetail = {
        type: 'urn:readybell:auth:invalid-token',
        title: 'Invalid Token',
        status: 401,
        detail: 'x', // Too short to use
      };

      const parsed = createParsedError(problemDetail);
      const error = new ApiError('API call failed', parsed);

      expect(error.name).toBe('ApiError');
      expect(error.message).toBe('API call failed');
      expect(error.statusCode).toBe(401);
      expect(error.retryable).toBe(false);
      expect(error.getUserMessage()).toContain('session has expired');
      expect(error.getErrorContext()).toBe('auth:invalid-token');
      expect(error.getProblemDetail()).toEqual(problemDetail);
    });

    it('should correctly identify retryable errors', () => {
      const retryableProblem: ProblemDetail = {
        type: 'urn:readybell:server:internal-error',
        title: 'Server Error',
        status: 500,
        detail: 'Internal error',
      };

      const notRetryableProblem: ProblemDetail = {
        type: 'urn:readybell:auth:invalid-token',
        title: 'Invalid Token',
        status: 401,
        detail: 'Token expired',
      };

      const error1 = new ApiError('Retryable', createParsedError(retryableProblem));
      const error2 = new ApiError('Not retryable', createParsedError(notRetryableProblem));

      expect(error1.isRetryable()).toBe(true);
      expect(error2.isRetryable()).toBe(false);
    });
  });

  describe('ERROR_MESSAGES mapping', () => {
    it('should have all common error contexts mapped', () => {
      const commonContexts: ErrorContext[] = [
        'auth:invalid-token',
        'auth:tenant-mismatch',
        'validation:invalid-request',
        'orders:not-found',
        'orders:concurrent-modification',
        'admin:staff-not-found',
      ];

      commonContexts.forEach(context => {
        expect(ERROR_MESSAGES).toHaveProperty(context);
        expect(typeof ERROR_MESSAGES[context]).toBe('string');
        expect(ERROR_MESSAGES[context]).toBeTruthy();
      });
    });

    it('should have user-friendly messages', () => {
      Object.values(ERROR_MESSAGES).forEach(message => {
        // Should not contain technical jargon
        expect(message).not.toMatch(/SQLException|NullPointerException|IndexOutOfBounds/);
        // Should be non-empty
        expect(message.length).toBeGreaterThan(5);
        // Should be lowercase or start with capital
        expect(/^[a-z]|^[A-Z]/.test(message)).toBe(true);
      });
    });
  });

  describe('Edge cases and error handling', () => {
    it('should handle malformed URN format gracefully', () => {
      const malformedUrns = [
        'urn:readybell',
        'urn:readybell:',
        'urn:readybell:auth',
        'readybell:auth:invalid-token',
        'urn:unknown:auth:invalid-token',
      ];

      malformedUrns.forEach(urn => {
        expect(() => extractErrorContext(urn)).not.toThrow();
      });
    });

    it('should handle missing optional fields in ProblemDetail', () => {
      const minimal: ProblemDetail = {
        type: 'urn:readybell:server:error',
        title: 'Error',
        status: 500,
        detail: 'Error occurred',
      };

      const parsed = createParsedError(minimal);

      expect(parsed.problemDetail.instance).toBeUndefined();
      expect(parsed.userMessage).toBeTruthy();
    });

    it('should handle very large status codes', () => {
      expect(mapStatusToErrorType(9999)).toBe('unknown'); // Not a valid HTTP status
      expect(isRetryable(9999)).toBe(false);
    });

    it('should handle negative status codes', () => {
      expect(mapStatusToErrorType(-1)).toBe('unknown');
      expect(isRetryable(-1)).toBe(false);
    });
  });

  describe('Compatibility with backend responses', () => {
    it('should parse actual RFC 7807 response format', () => {
      const backendResponse: ProblemDetail = {
        type: 'urn:readybell:orders:invalid-transition',
        title: 'Invalid State Transition',
        status: 400,
        detail: 'Cannot transition from READY to CREATED',
        instance: '/api/orders/550e8400-e29b-41d4-a716-446655440000/status',
      };

      const parsed = createParsedError(backendResponse);

      expect(parsed.errorContext).toBe('orders:invalid-transition');
      // Detail field is preferred when available and user-friendly
      expect(parsed.userMessage).toBe('Cannot transition from READY to CREATED');
    });

    it('should parse validation error with detail', () => {
      const backendResponse: ProblemDetail = {
        type: 'urn:readybell:validation:invalid-request',
        title: 'Validation Failed',
        status: 400,
        detail: 'Field "email" must be a valid email address',
        instance: '/api/admin/staff',
      };

      const parsed = createParsedError(backendResponse);

      expect(parsed.userMessage).toBe('Field "email" must be a valid email address');
    });

    it('should parse 409 Conflict for concurrent modifications', () => {
      const backendResponse: ProblemDetail = {
        type: 'urn:readybell:orders:concurrent-modification',
        title: 'Conflict',
        status: 409,
        detail: 'Order was modified since you last viewed it',
        instance: '/api/orders/550e8400-e29b-41d4-a716-446655440000',
      };

      const parsed = createParsedError(backendResponse);

      expect(parsed.errorType).toBe('conflict');
      expect(parsed.retryable).toBe(false);
    });
  });
});
