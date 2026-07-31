# Task 1.2.1 Completion Report: Create ProblemDetail Interface and Parser

## Task Summary
Implemented RFC 7807 Problem Detail error response parsing and handling in the Dashboard UI. This is the foundational error handling infrastructure required for all backend API integration.

**Task ID**: 1.2.1  
**Status**: ✅ COMPLETED  
**Time**: 2024-07-31

---

## What Was Implemented

### 1. **New File: `src/types/errors.ts`** (613 lines)
   
   - **ProblemDetail Interface**: RFC 7807 compliant error response type with fields:
     - `type`: URN format (urn:readybell:{context}:{error})
     - `title`: Human-readable title
     - `status`: HTTP status code
     - `detail`: Specific error description
     - `instance`: Optional request URI
     - `code`: Optional application error code

   - **Error Context Type**: Union of all known error contexts from backend:
     - Authentication: `auth:invalid-token`, `auth:tenant-mismatch`, `auth:insufficient-permissions`
     - Validation: `validation:invalid-request`, `validation:invalid-filter`, `validation:missing-required`
     - Orders: `orders:not-found`, `orders:invalid-transition`, `orders:concurrent-modification`
     - Admin: `admin:staff-not-found`, `admin:staff-duplicate`, `admin:restaurant-not-found`, etc.
     - Sessions: `session:invalid-qr`, `session:session-expired`
     - Server: `server:internal-error`, `server:database-error`, `server:service-unavailable`

   - **ERROR_MESSAGES Mapping**: User-friendly messages for all error contexts

   - **Core Parser Functions**:
     - `parseProblemDetail()`: Main entry point
     - `extractProblemDetail()`: Extract from Response or raw data
     - `createParsedError()`: Map to ParsedError with all metadata
     - `extractErrorContext()`: Parse URN type to error context
     - `mapErrorToUserMessage()`: Generate user-friendly message
     - `mapStatusToErrorType()`: Classify error (auth, validation, server, etc.)
     - `isRetryable()`: Determine if error can be retried
     - `mapStatusToSeverity()`: Log severity level
     - `handleErrorResponse()`: Main error handler factory
     - `logError()`: Structured error logging

   - **ParsedError Type**: Complete error metadata for UI handling:
     - `problemDetail`: Original RFC 7807 response
     - `userMessage`: User-friendly message
     - `errorType`: Classification (auth, validation, server, conflict, network, unknown)
     - `statusCode`: HTTP status
     - `retryable`: Whether error can be retried
     - `severity`: Log level (warning, error, critical)
     - `errorContext`: Parsed error context (if available)
     - `timestamp`: When error occurred

   - **ApiError Exception Class**: Custom error for exception handling
     - `getUserMessage()`: Get user-friendly message
     - `getErrorContext()`: Get error context
     - `isRetryable()`: Check if retryable
     - `getProblemDetail()`: Get original response
     - `log()`: Log with context

### 2. **Test File: `src/types/errors.test.ts`** (580 lines, 51 tests)

   Comprehensive test coverage including:
   - **Parser Tests**: Handling Response objects, raw ProblemDetail, errors
   - **Context Extraction**: URN parsing, unknown formats
   - **Message Mapping**: Error context to user message, detail field priority
   - **Classification Tests**: Status to error type, severity, retryability
   - **Edge Cases**: Malformed URN, missing fields, invalid status codes
   - **Backend Compatibility**: Real RFC 7807 response formats
   - **Exception Handling**: ApiError creation and methods

   **Test Results**: ✅ 51/51 tests passing

### 3. **Updated File: `src/types/index.ts`**
   - Exported all error types and functions from errors module
   - ProblemDetail, ErrorContext, ParsedError, ERROR_MESSAGES, etc.

---

## Key Features

### Message Priority System
The parser uses a smart priority system for user messages:
1. **Detail Field** (if user-friendly): Preferred for specific validation errors
2. **Error Context Mapping**: For common error patterns like "auth:invalid-token"
3. **Status-Based Generic**: Fallback generic message based on HTTP status

This ensures specific backend error messages (e.g., "Field 'email' must be valid") are shown to users, while filtering out technical errors.

### Error Classification
- **Auth**: 401 Unauthorized, 403 Forbidden
- **Validation**: 400 Bad Request, 422 Unprocessable Entity
- **Conflict**: 409 Concurrent Modification
- **Server**: 500-599 Server Errors
- **Network**: 0 (network error), 408 Request Timeout
- **Unknown**: Any other status code

### Retryability Logic
- **Retryable**: 5xx server errors (500-599), 408, 429, network errors (0)
- **Not Retryable**: 4xx client errors (except 408, 429), invalid codes

### Severity Mapping
- **Critical**: Auth failures (401, 403)
- **Error**: Server errors (5xx), network errors
- **Warning**: Client errors (4xx), recoverable issues

---

## Design Principles

1. **Non-Technical User Messages**: No database errors, stack traces, or internal details
2. **Specific > Generic**: Detail field preferred over generic ERROR_MESSAGES
3. **Request Context**: Logged with optional context (URL, method, request ID)
4. **Extensible**: Easy to add new error contexts and mappings
5. **Type-Safe**: Full TypeScript support with union types for contexts
6. **Testable**: All functions independently testable with comprehensive test suite

---

## Usage Examples

### Basic Usage
```typescript
try {
  const data = await api.get('/api/orders');
} catch (error) {
  const parsed = handleErrorResponse(error);
  console.error(parsed.userMessage);  // Show to user
  console.warn(`[${parsed.severity}]`, parsed);  // Log for debugging
}
```

### With RFC 7807 Response
```typescript
const problemDetail: ProblemDetail = {
  type: 'urn:readybell:orders:not-found',
  title: 'Not Found',
  status: 404,
  detail: 'Order #12345 not found',
  instance: '/api/orders/12345'
};

const parsed = createParsedError(problemDetail);
// parsed.userMessage = "Order #12345 not found"
// parsed.errorType = 'unknown'
// parsed.retryable = false
// parsed.severity = 'warning'
```

### Error Logging
```typescript
const error = new ApiError('Request failed', parsed);
error.log({
  url: '/api/orders',
  method: 'GET',
  requestId: 'abc-123'
});
// Logs: [ERROR] type: 'orders:not-found', detail: 'Order not found', ...
```

---

## Acceptance Criteria Met

✅ All error responses parsed as RFC 7807 Problem Details  
✅ Error type, title, status, and detail fields extracted  
✅ User-friendly error messages displayed (no technical jargon)  
✅ 401, 403, 404, 409, 5xx errors handled with specific logic  
✅ Sensitive data not exposed in error messages  
✅ Error logging configured for debugging (with severity levels)  
✅ TypeScript interfaces for all error types  
✅ Comprehensive test coverage (51 tests, all passing)  

---

## Integration Points

This foundation enables:
- **Task 1.2.2**: Implement error handler for all HTTP status codes
- **Task 1.2.3**: Map backend error types to user-friendly messages
- **Task 1.2.4**: Add retry logic for 5xx errors (exponential backoff)
- **Task 1.2.5**: Create ErrorBoundary component for UI error display
- **All Phase 1-3 tasks**: Consistent error handling across all endpoints

---

## Files Created

```
dashboard-ui/src/types/
├── errors.ts           (613 lines) - ProblemDetail interface and parser
├── errors.test.ts      (580 lines) - 51 comprehensive tests
└── index.ts            (updated) - Export error types
```

**Total**: 1,193 lines of code + tests

---

## Next Steps

1. **Task 1.2.2**: Implement HTTP status code error handler for all 4xx/5xx codes
2. **Task 1.2.3**: Integrate with API client for automatic error parsing
3. **Task 1.2.4**: Add retry logic with exponential backoff for 5xx errors
4. **Task 1.2.5**: Create ErrorBoundary React component for UI error display
5. **Testing**: E2E testing with real backend error responses

---

## References

- RFC 7807: https://tools.ietf.org/html/rfc7807
- Backend Error Format: See requirements.md Requirement 6
- Design Spec: design.md - API Client Layer, Error Handling section
