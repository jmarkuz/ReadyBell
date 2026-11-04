/**
 * API Client Configuration
 * Handles all HTTP requests to the backend with authentication, interceptors, and error handling
 * 
 * Base URL: same origin by default (Vite proxies /api to the backend); override with VITE_API_BASE_URL
 * 
 * Supports:
 * - Bearer token authentication (Authorization header)
 * - Tenant isolation (via authenticated user context)
 * - RFC 7807 error responses
 * - Multi-phase endpoint integration
 */

import { ErrorResponse, DisplayError, PaginatedResponse, OrderRow, OrderStatus, OrderMetrics } from '@/types';
import { tokenManager } from './TokenManager';

/**
 * User profile from authentication
 */
export interface UserProfile {
  id: string;
  username: string;
  email: string;
  name: string;
  role: 'STAFF' | 'RESTAURANT_ADMIN' | 'PLATFORM_ADMIN';
  tenantId: string;
  restaurantName?: string;
}

/**
 * Request interceptor type definition
 */
export type RequestInterceptor = (config: RequestConfig) => RequestConfig | Promise<RequestConfig>;

/**
 * Response interceptor type definition
 */
/** The request a response belongs to, so interceptors can replay it faithfully (method, headers, body). */
export interface RequestContext {
  url: string;
  init: RequestInit;
}

export type ResponseInterceptor = (
  response: Response,
  context?: RequestContext,
) => Response | Promise<Response>;

/**
 * Error interceptor type definition
 */
export type ErrorInterceptor = (error: Error) => Error | Promise<Error>;

/**
 * Request configuration for interceptors
 */
export interface RequestConfig {
  method: string;
  endpoint: string;
  body?: unknown;
  headers: HeadersInit;
  url?: string;
}

/**
 * Order row exactly as the backend sends it: dates are ISO strings and statuses use backend names.
 */
export type RawOrderRow = Omit<OrderRow, 'deliveryETA' | 'status'> & {
  deliveryETA: string | null;
  status: string;
};

export interface StaffAccountInfo {
  accountId: string;
  username: string;
  role: 'STAFF' | 'RESTAURANT_ADMIN';
  status: 'ACTIVE' | 'DEACTIVATED';
}

export interface RestaurantInfo {
  tenantId: string;
  identifier: string;
  name: string | null;
  status: 'ACTIVE' | 'DEACTIVATED';
  adminUsername: string | null;
}

/** One record of a test-data import file (see test-data/test-orders-50.json). */
export interface TestDataOrder {
  customerName?: string;
  estimatedMinutes?: number;
  status?: 'CREATED' | 'IN_PROGRESS' | 'READY' | 'COMPLETED' | 'CANCELLED';
  items?: Array<{ name: string; quantity: number }>;
}

/** Response of GET /api/orders/metrics (backend status names). */
interface RawOrderMetrics {
  totalOrders: number;
  created: number;
  inProgress: number;
  ready: number;
  completed: number;
  cancelled: number;
}

/** Backend → UI status names (the UI says PREPARING where the backend says IN_PROGRESS). */
export function fromBackendStatus(status: string): OrderStatus {
  return (status === 'IN_PROGRESS' ? 'PREPARING' : status) as OrderStatus;
}

/** UI → backend status names. */
export function toBackendStatus(status: string): string {
  return status === 'PREPARING' ? 'IN_PROGRESS' : status;
}

/** Converts a backend order row into the UI's {@link OrderRow} (Date objects, UI status names). */
export function normalizeOrderRow(raw: RawOrderRow): OrderRow {
  return {
    ...raw,
    status: fromBackendStatus(raw.status),
    deliveryETA: raw.deliveryETA ? new Date(raw.deliveryETA) : null,
  };
}

/**
 * API Client Class
 * Implements restaurant-notification-platform endpoint specification
 */
export class ApiClient {
  private baseURL: string;
  private defaultHeaders: HeadersInit;
  private userProfile: UserProfile | null = null;
  private requestInterceptors: RequestInterceptor[] = [];
  private responseInterceptors: ResponseInterceptor[] = [];
  private errorInterceptors: ErrorInterceptor[] = [];
  private refreshPromise: Promise<string | null> | null = null;

  constructor(baseURL?: string) {
    // Default: same origin. In dev the Vite proxy forwards /api to the backend, so no CORS is needed.
    this.baseURL = baseURL ?? import.meta.env.VITE_API_BASE_URL ?? '';
    this.defaultHeaders = {
      'Content-Type': 'application/json',
    };
    this.loadUserProfile();
    this.setupDefaultRequestInterceptor();
  }

  /**
   * Setup default request interceptor to inject Bearer token
   * This interceptor:
   * - Retrieves the stored bearer token from TokenManager
   * - Injects Authorization: Bearer {token} header
   * - Skips token injection if no token available (graceful fallback)
   * - Logs warning if token is missing (but allows request to proceed)
   */
  private setupDefaultRequestInterceptor(): void {
    this.addRequestInterceptor((config: RequestConfig) => {
      const token = tokenManager.get();
      
      if (token) {
        // Token available: inject it into Authorization header
        (config.headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
        console.debug(`[API] Request interceptor: Bearer token injected for ${config.method} ${config.endpoint}`);
      } else {
        // No token available: log warning but allow request to proceed
        console.warn(
          `[API] Request interceptor: No bearer token available for ${config.method} ${config.endpoint}. ` +
          `Request will proceed without authentication. This may result in 401 Unauthorized response.`
        );
      }
      
      return config;
    });

    // Setup response interceptor to handle 401 and token refresh
    this.setupDefaultResponseInterceptor();
  }

  /**
   * Setup default response interceptor to handle 401 Unauthorized responses
   * This interceptor:
   * - Detects 401 Unauthorized responses
   * - Attempts token refresh (if available)
   * - Retries original request with new token
   * - Logs out user if refresh fails or no refresh token available
   * - Handles edge cases: multiple simultaneous 401s via request queue
   */
  private setupDefaultResponseInterceptor(): void {
    this.addResponseInterceptor(async (response: Response, context?: RequestContext) => {
      // Only handle 401 Unauthorized responses
      if (response.status !== 401) {
        return response;
      }

      // A 401 from the auth endpoints themselves (wrong password, unrefreshable token) is an ordinary
      // error for the caller to show; it must not trigger refresh/logout/redirect.
      try {
        const path = new URL(response.url, window.location.origin).pathname;
        if (path === '/api/login' || path === '/api/auth/refresh') {
          return response;
        }
      } catch {
        // unparsable URL: fall through to the normal handling
      }

      console.warn(`[API] Response interceptor: 401 Unauthorized detected for ${response.url}`);

      // Parse error details if available
      try {
        const contentType = response.headers.get('content-type');
        if (contentType?.includes('json')) {
          const data = await response.clone().json();
          if (data.detail) {
            console.debug(`[API] Error detail: ${data.detail}`);
          }
        }
      } catch (error) {
        console.debug('Could not parse 401 error response body');
      }

      // If no token available, cannot refresh - redirect to login immediately
      const currentToken = tokenManager.get();
      if (!currentToken) {
        console.warn('[API] Response interceptor: No token available, cannot refresh. Logging out.');
        this.logout();
        window.location.href = '/login';
        return response;
      }

      // Attempt token refresh
      // Use a promise to prevent multiple simultaneous refresh attempts
      if (!this.refreshPromise) {
        console.debug('[API] Response interceptor: Initiating token refresh');
        this.refreshPromise = this.attemptTokenRefresh()
          .then((newToken) => {
            this.refreshPromise = null; // Clear the promise queue
            return newToken;
          })
          .catch((error) => {
            this.refreshPromise = null; // Clear the promise queue
            console.error('[API] Response interceptor: Token refresh failed:', error);
            return null;
          });
      } else {
        console.debug('[API] Response interceptor: Token refresh already in progress, waiting...');
      }

      // Wait for refresh to complete
      const newToken = await this.refreshPromise;

      if (newToken) {
        // Refresh succeeded - retry original request with new token
        console.debug('[API] Response interceptor: Token refresh successful, retrying original request');
        
        // Create a new request with the same URL, method, and body, but with updated Authorization header
        // Replay the original request (same method, headers and body) with the new token.
        // `context` is always provided by request(); the fallback only guards against direct callers.
        const url = context?.url ?? response.url;
        const retryOptions: RequestInit = context
          ? {
              ...context.init,
              headers: { ...(context.init.headers as Record<string, string>), Authorization: `Bearer ${newToken}` },
            }
          : {
              method: 'GET',
              headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${newToken}` },
            };

        const retryResponse = await fetch(url, retryOptions);
        return retryResponse;
      } else {
        // Refresh failed - logout and redirect to login
        console.warn('[API] Response interceptor: Token refresh failed, logging out');
        this.logout();
        window.location.href = '/login';
        return response;
      }
    });
  }

  /**
   * Attempt to refresh the bearer token using refresh endpoint
   * 
   * @returns Promise resolving to new token string if successful, null if refresh fails
   */
  /**
   * Attempt to refresh the bearer token with exponential backoff retry
   * 
   * Retry Strategy:
   * - Max 3 attempts total
   * - Exponential backoff: 1s, 2s, 4s between attempts
   * - No retry on permanent failures (401, 403, 400)
   * - Retry on transient failures (5xx, network errors, timeouts)
   * - Detailed logging of each attempt
   * 
   * @returns Promise resolving to new token string if successful, null if all retries exhausted
   */
  private async attemptTokenRefresh(): Promise<string | null> {
    const MAX_ATTEMPTS = 3;
    const BACKOFF_DELAYS = [1000, 2000, 4000]; // 1s, 2s, 4s in milliseconds

    // Get current token for refresh endpoint
    const currentToken = tokenManager.get();
    if (!currentToken) {
      console.warn('[API] attemptTokenRefresh: No current token available');
      return null;
    }

    console.debug('[API] Starting token refresh with retry strategy (max 3 attempts)');

    for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      try {
        console.debug(
          `[API] Token refresh attempt ${attempt + 1}/${MAX_ATTEMPTS} via POST /api/auth/refresh`
        );

        // Call refresh endpoint without going through the normal request method
        // to avoid infinite loops if refresh endpoint returns 401
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 10000); // 10 second timeout

        const response = await fetch(`${this.baseURL}/api/auth/refresh`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${currentToken}`,
          },
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        if (!response.ok) {
          const statusCode = response.status;
          console.warn(
            `[API] Token refresh attempt ${attempt + 1} failed with status ${statusCode}`
          );

          // Permanent failures - do not retry
          if (statusCode === 401 || statusCode === 403 || statusCode === 400) {
            if (statusCode === 401 || statusCode === 403) {
              console.warn(
                `[API] Token refresh rejected by server (${statusCode}), user likely logged out elsewhere. ` +
                'No retry will be attempted.'
              );
            } else if (statusCode === 400) {
              console.warn(
                '[API] Token refresh rejected with 400 Bad Request. No retry will be attempted.'
              );
            }
            return null;
          }

          // Transient failures - retry if attempts remaining
          if (statusCode >= 500 || (statusCode >= 502 && statusCode <= 504)) {
            console.error(
              `[API] Token refresh server error (${statusCode}). ` +
              `${attempt < MAX_ATTEMPTS - 1 ? `Will retry after ${BACKOFF_DELAYS[attempt]}ms` : 'No more retries'}`
            );

            if (attempt < MAX_ATTEMPTS - 1) {
              const delayMs = BACKOFF_DELAYS[attempt];
              console.debug(
                `[API] Waiting ${delayMs}ms before retry attempt ${attempt + 2}/${MAX_ATTEMPTS}`
              );
              await new Promise((resolve) => setTimeout(resolve, delayMs));
              continue; // Retry
            }
          }

          return null;
        }

        // Success - parse refresh response
        console.debug('[API] Token refresh attempt succeeded (status 200)');
        const data = await response.json();
        const newToken = data.token || data.accessToken;

        if (!newToken) {
          console.error('[API] Token refresh response missing token field');
          return null;
        }

        // Store new token
        const stored = tokenManager.store(newToken);
        if (!stored) {
          console.error('[API] Failed to store refreshed token');
          return null;
        }

        console.debug(
          `[API] Token successfully refreshed and stored after ${attempt + 1} attempt(s)`
        );
        return newToken;
      } catch (error) {
        // Network errors and timeouts - these are transient, retry if attempts remaining
        const isNetworkError = error instanceof TypeError && error.name === 'TypeError';
        const isAbortError = error instanceof DOMException && error.name === 'AbortError';
        const isTimeoutError = isAbortError;

        if (isNetworkError || isTimeoutError) {
          console.error(
            `[API] Token refresh attempt ${attempt + 1} failed with network/timeout error:`,
            error instanceof Error ? error.message : String(error)
          );

          if (attempt < MAX_ATTEMPTS - 1) {
            const delayMs = BACKOFF_DELAYS[attempt];
            console.debug(
              `[API] Network error detected. Waiting ${delayMs}ms before retry attempt ${attempt + 2}/${MAX_ATTEMPTS}`
            );
            await new Promise((resolve) => setTimeout(resolve, delayMs));
            continue; // Retry
          } else {
            console.error(
              `[API] Token refresh exhausted all ${MAX_ATTEMPTS} attempts due to network errors`
            );
            return null;
          }
        } else {
          // Unexpected error - don't retry
          console.error(
            '[API] Token refresh failed with unexpected error:',
            error instanceof Error ? error.message : String(error)
          );
          return null;
        }
      }
    }

    console.error(
      `[API] Token refresh exhausted all ${MAX_ATTEMPTS} retry attempts`
    );
    return null;
  }

  /**
   * Add a request interceptor
   * Interceptors are called in order before the request is sent
   */
  public addRequestInterceptor(interceptor: RequestInterceptor): void {
    this.requestInterceptors.push(interceptor);
  }

  /**
   * Add a response interceptor
   * Interceptors are called in order after response is received
   */
  public addResponseInterceptor(interceptor: ResponseInterceptor): void {
    this.responseInterceptors.push(interceptor);
  }

  /**
   * Add an error interceptor
   * Interceptors are called in order when an error occurs
   */
  public addErrorInterceptor(interceptor: ErrorInterceptor): void {
    this.errorInterceptors.push(interceptor);
  }

  /**
   * Load user profile from token or localStorage
   * Priority: Token (decoded JWT) > localStorage (fallback)
   */
  private loadUserProfile(): void {
    try {
      // Try to load from token first (more authoritative)
      const decodedToken = tokenManager.getDecodedToken();
      if (decodedToken) {
        this.userProfile = {
          id: decodedToken.sub || decodedToken.id || decodedToken.username || '',
          // the backend puts the username in the standard `sub` claim
          username: decodedToken.username || decodedToken.sub || '',
          email: decodedToken.email || '',
          name: decodedToken.name || decodedToken.username || decodedToken.sub || '',
          role: decodedToken.role,
          tenantId: decodedToken.tenant_id || decodedToken.tenantId || '',
        };
        return;
      }

      // Fallback to localStorage
      const profileJson = localStorage.getItem('user-profile');
      if (profileJson) {
        this.userProfile = JSON.parse(profileJson);
      }
    } catch (error) {
      console.warn('Failed to load user profile:', error);
    }
  }

  /**
   * Build request headers with default headers
   * Note: Bearer token is injected by request interceptor, not here
   */
  private getHeaders(): HeadersInit {
    return { ...this.defaultHeaders };
  }

  /**
   * Get current user profile
   */
  getUserProfile(): UserProfile | null {
    return this.userProfile;
  }

  /**
   * Get current tenant ID
   */
  getTenantId(): string | null {
    return this.userProfile?.tenantId || null;
  }

  /**
   * Get current user role
   */
  getUserRole(): string | null {
    return this.userProfile?.role || null;
  }

  /**
   * Map backend error response to frontend display error
   */
  private mapError(response: Response, data: unknown): DisplayError {
    let errorData: Partial<ErrorResponse> = {};
    
    if (data && typeof data === 'object' && 'type' in data) {
      errorData = data as Partial<ErrorResponse>;
    }

    // Determine error type based on HTTP status and error type
    let type: DisplayError['type'] = 'unknown';
    if (response.status === 401 || response.status === 403) {
      type = 'auth';
    } else if (response.status === 400 || response.status === 422) {
      type = 'validation';
    } else if (response.status >= 500) {
      type = 'server';
    } else if (response.status === 0) {
      type = 'network';
    }

    // Build user-friendly error message
    let message = '';
    switch (response.status) {
      case 400:
        message = errorData.detail || 'Invalid request';
        break;
      case 401:
        message = 'Session expired. Please log in again.';
        break;
      case 403:
        message = 'You don\'t have permission to access this resource.';
        break;
      case 404:
        message = errorData.detail || 'Resource not found.';
        break;
      case 405:
      case 415:
        message = errorData.detail || `Request not supported (${response.status}).`;
        break;
      case 409:
        message = 'Order was updated by another user. Refreshing...';
        break;
      case 422:
        message = errorData.detail || 'Invalid data provided.';
        break;
      case 504:
      case 503:
      case 502:
        message = 'Server is temporarily unavailable. Please try again.';
        break;
      default:
        message = errorData.detail || `Error (${response.status})`;
    }

    return {
      type,
      message,
      code: errorData.code,
      retryable: response.status >= 500 || response.status === 408 || response.status === 429,
      timestamp: new Date(),
    };
  }

  /**
   * Perform HTTP request with error handling and interceptors
   * 
   * Flow:
   * 1. Apply request interceptors (e.g., inject Bearer token)
   * 2. Send HTTP request
   * 3. Apply response interceptors
   * 4. Handle errors with error interceptors
   */
  private async request<T>(
    method: string,
    endpoint: string,
    body?: unknown
  ): Promise<T> {
    const url = `${this.baseURL}${endpoint}`;

    try {
      // Build initial request config
      let config: RequestConfig = {
        method,
        endpoint,
        body,
        headers: this.getHeaders(),
      };

      // Apply request interceptors in order
      for (const interceptor of this.requestInterceptors) {
        config = await interceptor(config);
      }

      const options: RequestInit = {
        method: config.method,
        headers: config.headers,
      };

      if (config.body) {
        options.body = JSON.stringify(config.body);
      }

      // Execute request
      let response = await fetch(url, options);

      // Apply response interceptors in order
      for (const interceptor of this.responseInterceptors) {
        response = await interceptor(response, { url, init: options });
      }

      // Parse response body
      let data: unknown;
      const contentType = response.headers.get('content-type');
      if (contentType?.includes('json')) {
        try {
          data = await response.json();
        } catch (error) {
          console.warn('Failed to parse JSON response:', error);
          data = null;
        }
      } else {
        data = await response.text();
      }

      // Handle errors
      if (!response.ok) {
        const error = this.mapError(response, data);
        throw new ApiError(error.message, response.status, error);
      }

      return data as T;
    } catch (error) {
      // Apply error interceptors
      let processedError = error instanceof Error ? error : new Error(String(error));
      for (const interceptor of this.errorInterceptors) {
        processedError = await interceptor(processedError);
      }

      // Handle network errors or propagate API errors
      if (processedError instanceof ApiError) {
        throw processedError;
      }

      if (processedError instanceof TypeError && processedError.message.includes('fetch')) {
        const displayError: DisplayError = {
          type: 'network',
          message: 'Network error. Check your connection and try again.',
          retryable: true,
          timestamp: new Date(),
        };
        throw new ApiError(displayError.message, 0, displayError);
      }

      throw processedError;
    }
  }

  /**
   * GET request
   */
  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>('GET', endpoint);
  }

  /**
   * POST request
   */
  async post<T>(endpoint: string, body: unknown): Promise<T> {
    return this.request<T>('POST', endpoint, body);
  }

  /**
   * PUT request
   */
  async put<T>(endpoint: string, body: unknown): Promise<T> {
    return this.request<T>('PUT', endpoint, body);
  }

  /**
   * PATCH request
   */
  async patch<T>(endpoint: string, body: unknown): Promise<T> {
    return this.request<T>('PATCH', endpoint, body);
  }

  /**
   * DELETE request
   */
  async delete<T>(endpoint: string): Promise<T> {
    return this.request<T>('DELETE', endpoint);
  }

  // ============================================================================
  // ORDER MANAGEMENT ENDPOINTS
  // ============================================================================

  /**
   * Get order list with pagination and filtering
   * GET /api/orders?page=1&pageSize=25&status=CREATED&status=READY&sortBy=created&sortOrder=desc&from=…&to=…
   */
  async getOrders(params?: {
    page?: number;
    pageSize?: number;
    status?: string[];
    sortBy?: 'eta' | 'status' | 'created' | 'customerName' | 'orderId';
    sortOrder?: 'asc' | 'desc';
    search?: string;
    /** Only orders created at or after this instant (ISO-8601). */
    from?: string;
    /** Only orders created at or before this instant (ISO-8601). */
    to?: string;
  }): Promise<PaginatedResponse<OrderRow>> {
    const queryParams = new URLSearchParams();

    if (params?.page) queryParams.append('page', params.page.toString());
    if (params?.pageSize) queryParams.append('pageSize', params.pageSize.toString());
    if (params?.status?.length) {
      params.status.forEach(status => queryParams.append('status', toBackendStatus(status)));
    }
    if (params?.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params?.sortOrder) queryParams.append('sortOrder', params.sortOrder);
    if (params?.search) queryParams.append('search', params.search);
    if (params?.from) queryParams.append('from', params.from);
    if (params?.to) queryParams.append('to', params.to);

    const queryString = queryParams.toString();
    const endpoint = `/api/orders${queryString ? '?' + queryString : ''}`;

    const raw = await this.get<PaginatedResponse<RawOrderRow>>(endpoint);
    return { ...raw, items: raw.items.map(normalizeOrderRow) };
  }

  /**
   * Order counts per status for the dashboard cards (the tenant's real totals, not just one page).
   * GET /api/orders/metrics
   */
  async getOrderMetrics(): Promise<OrderMetrics> {
    const raw = await this.get<RawOrderMetrics>('/api/orders/metrics');
    return {
      totalOrders: raw.totalOrders,
      newOrders: raw.created,
      preparing: raw.inProgress,
      readyForPickup: raw.ready,
      completed: raw.completed,
      cancelled: raw.cancelled,
    };
  }

  /**
   * Single-use ticket (valid 30 s) for opening the staff SSE stream; EventSource cannot send the JWT header.
   * POST /api/orders/stream/ticket
   */
  async createStreamTicket(): Promise<string> {
    const res = await this.post<{ ticket: string; expiresIn: number }>('/api/orders/stream/ticket', {});
    return res.ticket;
  }

  /**
   * Create a new order
   * POST /api/orders
   */
  async createOrder(data: {
    /** Omit to let the server start a new customer session (the receipt QR links to it). */
    sessionId?: string;
    customerName?: string;
    /** Minutes from now until the order should be ready (0–1440). */
    estimatedMinutes?: number;
    items?: Array<{ name: string; quantity: number }>;
  }): Promise<{ id: string; orderId: string; sessionId: string; status: string; version: number; createdAt: string; updatedAt: string }> {
    return this.post('/api/orders', data);
  }

  /**
   * Update order status
   * PATCH /api/orders/{id}/status
   * 
   * Valid transitions:
   * CREATED → IN_PROGRESS, CANCELLED
   * IN_PROGRESS → READY, CANCELLED
   * READY → COMPLETED, CANCELLED
   * COMPLETED, CANCELLED → (terminal, no transitions)
   */
  async updateOrderStatus(
    orderId: string,
    status: 'IN_PROGRESS' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED'
  ): Promise<{ id: string; status: string; updatedAt: string }> {
    return this.patch(`/api/orders/${orderId}/status`, { status: toBackendStatus(status) });
  }

  /**
   * Dev only: fills the restaurant with generated orders.
   * POST /api/dev/test-data (backend `dev` profile)
   */
  async generateTestData(count = 12): Promise<{ created: number }> {
    return this.post(`/api/dev/test-data?count=${count}`, {});
  }

  /**
   * Dev only: imports orders parsed from a test-data file.
   * POST /api/dev/test-data/import (backend `dev` profile)
   */
  async importTestData(orders: TestDataOrder[]): Promise<{ created: number }> {
    return this.post('/api/dev/test-data/import', { orders });
  }

  /**
   * Dev only: removes the generated orders again.
   * DELETE /api/dev/test-data (backend `dev` profile)
   */
  async clearTestData(): Promise<{ deleted: number }> {
    return this.delete('/api/dev/test-data');
  }

  // ============================================================================
  // AUTHENTICATION ENDPOINTS
  // ============================================================================

  /**
   * Login with credentials
   * POST /api/login
   */
  async login(credentials: {
    username: string;
    password: string;
  }): Promise<{ token: string; role: string; tenantId: string; expiresIn: number }> {
    const response = await this.post('/api/login', credentials);
    
    // Store token and user profile
    const typedResponse = response as {
      token: string;
      role: string;
      tenantId: string;
      username?: string;
      email?: string;
      name?: string;
      expiresIn: number;
    };

    if (typedResponse.token) {
      // Same store the request interceptor and AuthProvider read from
      tokenManager.store(typedResponse.token);

      const userProfile: UserProfile = {
        id: typedResponse.username || '',
        username: typedResponse.username || '',
        email: typedResponse.email || '',
        name: typedResponse.name || typedResponse.username || '',
        role: typedResponse.role as UserProfile['role'],
        tenantId: typedResponse.tenantId,
      };
      
      localStorage.setItem('user-profile', JSON.stringify(userProfile));
      this.userProfile = userProfile;
    }

    return typedResponse;
  }

  /**
   * Logout (clear tokens)
   */
  logout(): void {
    // Clear token via TokenManager
    tokenManager.clear();
    
    // Clear user profile
    localStorage.removeItem('user-profile');
    localStorage.removeItem('auth-token'); // Legacy fallback
    this.userProfile = null;
    
    console.debug('[API] User logged out, tokens cleared');
  }

  /**
   * Check if user is authenticated
   * Reloads profile to ensure we have latest token data
   */
  isAuthenticated(): boolean {
    // Reload profile to check current token status
    this.loadUserProfile();
    return !!tokenManager.get() && !!this.userProfile;
  }

  // ============================================================================
  // RESTAURANT STAFF ADMIN ENDPOINTS
  // ============================================================================

  /**
   * The signed-in user's own restaurant (any staff role)
   * GET /api/restaurant
   */
  async getRestaurantProfile(): Promise<{ name: string; identifier: string }> {
    return this.get('/api/restaurant');
  }

  /**
   * Staff accounts of the restaurant (RESTAURANT_ADMIN only)
   * GET /api/admin/staff
   */
  async listStaff(): Promise<StaffAccountInfo[]> {
    return this.get('/api/admin/staff');
  }

  /**
   * All restaurants (PLATFORM_ADMIN only)
   * GET /api/platform/restaurants
   */
  async listRestaurants(): Promise<RestaurantInfo[]> {
    return this.get('/api/platform/restaurants');
  }

  /**
   * Create a staff account (RESTAURANT_ADMIN only)
   * POST /api/admin/staff
   */
  async createStaffAccount(data: {
    username: string;
    password: string;
    role: 'STAFF' | 'RESTAURANT_ADMIN';
  }): Promise<{ accountId: string; username: string; role: string; status: string }> {
    return this.post('/api/admin/staff', data);
  }

  /**
   * Deactivate a staff account (RESTAURANT_ADMIN only)
   * DELETE /api/admin/staff/{accountId}
   */
  async deactivateStaffAccount(
    accountId: string
  ): Promise<{ accountId: string; username: string; role: string; status: string }> {
    return this.delete(`/api/admin/staff/${accountId}`);
  }

  /**
   * Rename the restaurant (RESTAURANT_ADMIN only)
   * PUT /api/admin/config
   */
  async updateRestaurantConfig(data: { name: string }): Promise<{ name: string }> {
    return this.put('/api/admin/config', data);
  }

  /**
   * Onboard a restaurant, optionally with its first admin (PLATFORM_ADMIN only)
   * POST /api/platform/restaurants
   *
   * `adminUsername` and `adminPassword` (min 8 characters) must be given together.
   */
  async createRestaurant(data: {
    identifier: string;
    name: string;
    adminUsername?: string;
    adminPassword?: string;
  }): Promise<RestaurantInfo> {
    return this.post('/api/platform/restaurants', data);
  }

  /**
   * Deactivate a restaurant (PLATFORM_ADMIN only)
   * DELETE /api/platform/restaurants/{tenantId}
   */
  async deactivateRestaurant(
    tenantId: string
  ): Promise<RestaurantInfo> {
    return this.delete(`/api/platform/restaurants/${tenantId}`);
  }

}

/**
 * API Error Class
 * Custom error with display information for the UI
 */
export class ApiError extends Error {
  public readonly statusCode: number;
  public readonly displayError: DisplayError;

  constructor(message: string, statusCode: number, displayError: DisplayError) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.displayError = displayError;
  }
}

/**
 * Create singleton instance
 */
export const apiClient = new ApiClient();
