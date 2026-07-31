/**
 * Error Handling Types and Utilities
 * 
 * Implements RFC 7807 Problem Detail error responses
 * https://tools.ietf.org/html/rfc7807
 * 
 * Backend returns errors in format:
 * {
 *   "type": "urn:readybell:{context}:{error}",
 *   "title": "Human-readable title",
 *   "status": 400,
 *   "detail": "Specific description of what went wrong",
 *   "instance": "/api/orders/12345"
 * }
 */

/* ============================================================================
   RFC 7807 PROBLEM DETAIL TYPES
   ============================================================================ */

/**
 * RFC 7807 Problem Detail response from backend
 * 
 * Standardized error format returned by all backend endpoints
 * Frontend must parse this format and map to user-friendly messages
 */
export interface ProblemDetail {
  /** URN format: "urn:readybell:{context}:{error}" */
  type: string;
  
  /** Human-readable title for the error */
  title: string;
  
  /** HTTP status code */
  status: number;
  
  /** Specific description of what went wrong */
  detail: string;
  
  /** Optional: Request URI that caused the error */
  instance?: string;
  
  /** Optional: Application-specific error code */
  code?: string;
  
  /** Optional: Additional error properties */
  [key: string]: unknown;
}

/* ============================================================================
   ERROR CONTEXT TYPES - Common error types from backend
   ============================================================================ */

/**
 * Error context and code combinations
 * Format: urn:readybell:{context}:{error}
 * 
 * Examples:
 * - urn:readybell:auth:invalid-token (401)
 * - urn:readybell:auth:tenant-mismatch (403)
 * - urn:readybell:validation:invalid-request (400)
 * - urn:readybell:orders:not-found (404)
 * - urn:readybell:orders:invalid-transition (400)
 * - urn:readybell:orders:concurrent-modification (409)
 */
export type ErrorContext =
  // Authentication & Authorization (4xx)
  | 'auth:invalid-token'              // 401 - Token expired, invalid, or malformed
  | 'auth:tenant-mismatch'            // 403 - User attempting to access different tenant
  | 'auth:insufficient-permissions'   // 403 - User role does not allow this action
  
  // Validation (400)
  | 'validation:invalid-request'      // 400 - Request body or parameters invalid
  | 'validation:invalid-filter'       // 400 - Filter parameter value invalid
  | 'validation:invalid-sort'         // 400 - Sort parameter value invalid
  | 'validation:missing-required'     // 400 - Required field missing
  
  // Orders (4xx)
  | 'orders:not-found'                // 404 - Order does not exist
  | 'orders:invalid-transition'       // 400 - Invalid status transition
  | 'orders:concurrent-modification'  // 409 - Order version conflict (optimistic lock)
  
  // Admin (4xx)
  | 'admin:staff-not-found'           // 404 - Staff account not found
  | 'admin:staff-duplicate'           // 409 - Staff username or email already exists
  | 'admin:config-not-found'          // 404 - Restaurant config not found
  | 'admin:restaurant-not-found'      // 404 - Restaurant/tenant not found
  | 'admin:restaurant-inactive'       // 403 - Restaurant is inactive
  | 'admin:restaurant-duplicate'      // 409 - Restaurant name or tenant already exists
  
  // Customer Sessions (4xx)
  | 'session:invalid-qr'              // 400 - QR code token invalid or expired
  | 'session:session-expired'         // 410 - Customer session expired
  | 'session:session-not-found'       // 404 - Session does not exist
  
  // Server Errors (5xx)
  | 'server:internal-error'           // 500 - Unspecified server error
  | 'server:database-error'           // 500 - Database operation failed
  | 'server:service-unavailable'      // 503 - Backend service temporarily unavailable;

/**
 * Mapping from error context/status to user-friendly messages
 */
export const ERROR_MESSAGES: Record<ErrorContext, string> = {
  // Authentication & Authorization
  'auth:invalid-token': 'Your session has expired. Please log in again.',
  'auth:tenant-mismatch': 'You don\'t have access to this resource.',
  'auth:insufficient-permissions': 'You don\'t have permission to perform this action.',
  
  // Validation
  'validation:invalid-request': 'The request contains invalid data. Please check your input.',
  'validation:invalid-filter': 'The filter value is not valid. Please try again.',
  'validation:invalid-sort': 'The sort option is not valid. Please try again.',
  'validation:missing-required': 'Required information is missing. Please fill in all fields.',
  
  // Orders
  'orders:not-found': 'Order not found. It may have been deleted.',
  'orders:invalid-transition': 'Cannot change order status. Please refresh and try again.',
  'orders:concurrent-modification': 'Order was updated by another user. Refreshing order details...',
  
  // Admin
  'admin:staff-not-found': 'Staff account not found.',
  'admin:staff-duplicate': 'Staff account with this username or email already exists.',
  'admin:config-not-found': 'Restaurant configuration not found.',
  'admin:restaurant-not-found': 'Restaurant not found.',
  'admin:restaurant-inactive': 'This restaurant is currently inactive.',
  'admin:restaurant-duplicate': 'Restaurant with this name already exists.',
  
  // Customer Sessions
  'session:invalid-qr': 'QR code is invalid or expired. Please try scanning again.',
  'session:session-expired': 'Your session has expired. Please start a new session.',
  'session:session-not-found': 'Session not found. Please start a new session.',
  
  // Server Errors
  'server:internal-error': 'Something went wrong on the server. Please try again.',
  'server:database-error': 'A database error occurred. Please try again.',
  'server:service-unavailable': 'The service is temporarily unavailable. Please try again later.',
};

/* ============================================================================
   PARSED ERROR TYPES - Frontend display format
   ============================================================================ */

/**
 * Parsed error for display in UI
 * Maps backend ProblemDetail to frontend-friendly format
 */
export interface ParsedError {
  /** The original RFC 7807 ProblemDetail from backend */
  problemDetail: ProblemDetail;
  
  /** User-friendly message to display */
  userMessage: string;
  
  /** Error type for routing/handling (auth, validation, server, etc.) */
  errorType: 'auth' | 'validation' | 'server' | 'network' | 'conflict' | 'unknown';
  
  /** HTTP status code */
  statusCode: number;
  
  /** Whether this error can be retried */
  retryable: boolean;
  
  /** Log severity level */
  severity: 'warning' | 'error' | 'critical';
  
  /** Parsed error context if available */
  errorContext?: ErrorContext;
  
  /** When the error occurred */
  timestamp: Date;
}

/* ============================================================================
   ERROR PARSER - RFC 7807 RESPONSE PARSER
   ============================================================================ */

/**
 * Parse RFC 7807 Problem Detail error response from backend
 * 
 * @param response - The Response object or raw data
 * @returns ParsedError with user-friendly message
 * 
 * Examples:
 * - parseProblemDetail(response) // from fetch Response
 * - parseProblemDetail(errorData) // from raw error object
 */
export function parseProblemDetail(response: Response | unknown): ParsedError {
  const problemDetail = extractProblemDetail(response);
  return createParsedError(problemDetail);
}

/**
 * Extract ProblemDetail from Response or raw data
 * 
 * Handles multiple input formats:
 * 1. Response object with JSON body (from fetch)
 * 2. Raw ProblemDetail object (already parsed)
 * 3. Error object (fallback)
 * 
 * @param input - Response or raw data
 * @returns ProblemDetail object
 */
export function extractProblemDetail(input: Response | unknown): ProblemDetail {
  // If it's already a ProblemDetail object, return it
  if (
    input &&
    typeof input === 'object' &&
    'type' in input &&
    'title' in input &&
    'status' in input &&
    'detail' in input
  ) {
    return input as ProblemDetail;
  }

  // If it's a Response object or has Response-like properties
  if (input instanceof Response || (input && typeof input === 'object' && 'status' in input && 'statusText' in input)) {
    const response = input as Partial<Response>;
    const status = typeof response.status === 'number' ? response.status : 0;
    const statusText = typeof response.statusText === 'string' ? response.statusText : 'Unknown error';
    const url = typeof response.url === 'string' ? response.url : undefined;

    return {
      type: `urn:readybell:server:http-${status}`,
      title: `HTTP ${status}`,
      status,
      detail: statusText,
      instance: url,
    };
  }

  // Fallback: generic error
  return {
    type: 'urn:readybell:server:unknown-error',
    title: 'Unknown Error',
    status: 0,
    detail: 'An unexpected error occurred. Please try again.',
  };
}

/**
 * Create ParsedError from ProblemDetail
 * Maps backend error to user-friendly message and logging info
 * 
 * @param problemDetail - RFC 7807 Problem Detail from backend
 * @returns ParsedError with all metadata for frontend handling
 */
export function createParsedError(problemDetail: ProblemDetail): ParsedError {
  const errorContext = extractErrorContext(problemDetail.type);
  const userMessage = mapErrorToUserMessage(problemDetail, errorContext);
  const errorType = mapStatusToErrorType(problemDetail.status);
  const retryable = isRetryable(problemDetail.status);
  const severity = mapStatusToSeverity(problemDetail.status);

  return {
    problemDetail,
    userMessage,
    errorType,
    statusCode: problemDetail.status,
    retryable,
    severity,
    errorContext,
    timestamp: new Date(),
  };
}

/**
 * Extract error context from URN type
 * 
 * Examples:
 * - "urn:readybell:auth:invalid-token" → "auth:invalid-token"
 * - "urn:readybell:orders:not-found" → "orders:not-found"
 * - "unknown" → undefined
 * 
 * @param type - URN type from ProblemDetail
 * @returns Error context or undefined
 */
export function extractErrorContext(type: string): ErrorContext | undefined {
  // Match "urn:readybell:{context}:{error}"
  const match = type.match(/^urn:readybell:(\w+):([a-z-]+)$/);
  if (match) {
    const context = `${match[1]}:${match[2]}` as ErrorContext;
    // Validate it's a known error context
    if (context in ERROR_MESSAGES) {
      return context;
    }
  }
  return undefined;
}

/**
 * Map error to user-friendly message
 * Priority:
 * 1. Backend detail field (if available and user-friendly) - for specific validation errors
 * 2. Specific error context mapping (e.g., "auth:invalid-token") - for general errors
 * 3. Generic status-based message
 * 
 * @param problemDetail - RFC 7807 Problem Detail
 * @param errorContext - Parsed error context (optional)
 * @returns User-friendly error message
 */
export function mapErrorToUserMessage(
  problemDetail: ProblemDetail,
  errorContext?: ErrorContext
): string {
  // 1. Use backend detail field if it looks user-friendly
  // Prefer detail for validation errors (often has specific field validation messages)
  if (
    problemDetail.detail &&
    !problemDetail.detail.includes('SQLException') &&
    !problemDetail.detail.includes('NullPointerException') &&
    !problemDetail.detail.includes('IndexOutOfBounds') &&
    problemDetail.detail.length > 5
  ) {
    return problemDetail.detail;
  }

  // 2. Use specific error context mapping if available
  if (errorContext && errorContext in ERROR_MESSAGES) {
    return ERROR_MESSAGES[errorContext];
  }

  // 3. Generic status-based message
  switch (problemDetail.status) {
    case 400:
      return 'The request contains invalid data. Please check your input.';
    case 401:
      return 'Your session has expired. Please log in again.';
    case 403:
      return 'You don\'t have permission to access this resource.';
    case 404:
      return 'The requested resource was not found.';
    case 409:
      return 'The resource was modified by another user. Please refresh and try again.';
    case 422:
      return 'The request data is invalid. Please check your input.';
    case 429:
      return 'Too many requests. Please wait a moment and try again.';
    case 500:
    case 502:
    case 503:
    case 504:
      return 'The server encountered an error. Please try again later.';
    default:
      return `Error (${problemDetail.status}): ${problemDetail.title || 'Unknown error'}`;
  }
}

/**
 * Map HTTP status code to error type for categorization
 * 
 * @param status - HTTP status code
 * @returns Error type
 */
export function mapStatusToErrorType(
  status: number
): 'auth' | 'validation' | 'server' | 'network' | 'conflict' | 'unknown' {
  if (status === 401 || status === 403) {
    return 'auth';
  }
  if (status === 400 || status === 422) {
    return 'validation';
  }
  if (status === 409) {
    return 'conflict';
  }
  // Server errors: 500-599 (but not invalid codes)
  if (status >= 500 && status <= 599) {
    return 'server';
  }
  if (status === 0 || status === 408) {
    return 'network';
  }
  // For any 4xx that's not covered above, return unknown
  if (status >= 400 && status < 500) {
    return 'unknown';
  }
  // For any other invalid status code
  return 'unknown';
}

/**
 * Determine if an error is retryable
 * 
 * Retryable:
 * - 5xx server errors (may be transient) - but only valid HTTP 5xx codes (500-599)
 * - 408 Request Timeout
 * - 429 Too Many Requests (rate limited, can retry with backoff)
 * - Network errors (status 0)
 * 
 * Not retryable:
 * - 4xx client errors (except 408, 429)
 * - Permanent auth failures (401, 403 without refresh)
 * - Invalid/unknown status codes
 * 
 * @param status - HTTP status code
 * @returns true if error can be retried
 */
export function isRetryable(status: number): boolean {
  // Valid 5xx errors are retryable (500-599)
  if (status >= 500 && status <= 599) {
    return true;
  }
  // Specific retryable 4xx codes
  if (status === 408 || status === 429) {
    return true;
  }
  // Network errors
  if (status === 0) {
    return true;
  }
  // Everything else is not retryable
  return false;
}

/**
 * Map HTTP status to log severity level
 * 
 * Severity mapping:
 * - 4xx: warning (client error, user can fix)
 * - 5xx: error (server error, user cannot fix)
 * - 401/403 + other critical: critical (immediate action needed)
 * 
 * @param status - HTTP status code
 * @returns Log severity level
 */
export function mapStatusToSeverity(
  status: number
): 'warning' | 'error' | 'critical' {
  if (status === 0) {
    return 'error'; // Network error
  }
  if (status < 400) {
    return 'warning'; // Shouldn't happen for errors
  }
  if (status < 500) {
    // 4xx client errors are warnings (user can usually fix)
    if (status === 401 || status === 403) {
      return 'critical'; // Auth failures need immediate attention
    }
    return 'warning';
  }
  // 5xx server errors are errors
  return 'error';
}

/* ============================================================================
   ERROR LOGGING UTILITY
   ============================================================================ */

/**
 * Log parsed error with appropriate severity
 * 
 * Logs:
 * - Error context and type
 * - User message
 * - Detailed problem information
 * - Stack trace (if available)
 * - Request context (URL, method)
 * 
 * @param parsedError - Parsed error to log
 * @param context - Optional additional context (URL, method, etc.)
 */
export function logError(
  parsedError: ParsedError,
  context?: Record<string, unknown>
): void {
  const { severity, errorContext, statusCode, problemDetail, userMessage } = parsedError;

  const logData = {
    timestamp: new Date().toISOString(),
    severity,
    errorType: parsedError.errorType,
    errorContext,
    statusCode,
    type: problemDetail.type,
    title: problemDetail.title,
    detail: problemDetail.detail,
    instance: problemDetail.instance,
    userMessage,
    ...context,
  };

  // Use appropriate log level based on severity
  switch (severity) {
    case 'critical':
      console.error('[CRITICAL ERROR]', logData);
      break;
    case 'error':
      console.error('[ERROR]', logData);
      break;
    case 'warning':
      console.warn('[WARNING]', logData);
      break;
  }
}

/* ============================================================================
   ERROR CLASS FOR EXCEPTIONS
   ============================================================================ */

/**
 * API Error exception
 * Wraps parsed error information for exception handling
 */
export class ApiError extends Error {
  public readonly parsedError: ParsedError;
  public readonly statusCode: number;
  public readonly retryable: boolean;

  constructor(message: string, parsedError: ParsedError) {
    super(message);
    this.name = 'ApiError';
    this.parsedError = parsedError;
    this.statusCode = parsedError.statusCode;
    this.retryable = parsedError.retryable;
  }

  /**
   * Get user-friendly error message
   */
  getUserMessage(): string {
    return this.parsedError.userMessage;
  }

  /**
   * Get error context (if available)
   */
  getErrorContext(): ErrorContext | undefined {
    return this.parsedError.errorContext;
  }

  /**
   * Is this error retryable?
   */
  isRetryable(): boolean {
    return this.retryable;
  }

  /**
   * Get original problem detail
   */
  getProblemDetail(): ProblemDetail {
    return this.parsedError.problemDetail;
  }

  /**
   * Log this error with context
   */
  log(context?: Record<string, unknown>): void {
    logError(this.parsedError, context);
  }
}

/* ============================================================================
   ERROR HANDLER FACTORY
   ============================================================================ */

/**
 * Handle error response and return structured error
 * 
 * Usage:
 * ```typescript
 * try {
 *   const data = await api.get('/api/orders');
 * } catch (error) {
 *   const parsedError = handleErrorResponse(error);
 *   console.error(parsedError.userMessage);
 * }
 * ```
 * 
 * @param error - Error from try/catch or fetch
 * @returns ParsedError with all metadata
 */
export function handleErrorResponse(error: unknown): ParsedError {
  // If it's a Response object or Response-like object
  if (error instanceof Response || (error && typeof error === 'object' && 'status' in error && 'statusText' in error)) {
    const problemDetail = extractProblemDetail(error);
    return createParsedError(problemDetail);
  }

  // If it's a plain object with ProblemDetail structure
  if (error && typeof error === 'object' && 'type' in error && 'title' in error && 'status' in error && 'detail' in error) {
    return createParsedError(error as ProblemDetail);
  }

  // If it's an Error with response data
  if (error instanceof Error) {
    // Try to extract from error message or properties
    const message = error.message || 'Unknown error';
    
    // Create generic error
    const problemDetail: ProblemDetail = {
      type: 'urn:readybell:server:unknown-error',
      title: 'Error',
      status: 0,
      detail: message,
    };

    return createParsedError(problemDetail);
  }

  // Fallback: generic error
  const problemDetail: ProblemDetail = {
    type: 'urn:readybell:server:unknown-error',
    title: 'Unknown Error',
    status: 0,
    detail: 'An unexpected error occurred',
  };

  return createParsedError(problemDetail);
}
