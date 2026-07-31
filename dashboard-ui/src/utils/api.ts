/**
 * API Client Configuration
 * Handles all HTTP requests to the backend with authentication, interceptors, and error handling
 * 
 * Base URL: configurable via VITE_API_BASE_URL env var or defaults to http://localhost:8080
 * 
 * Supports:
 * - Bearer token authentication (Authorization header)
 * - Tenant isolation (via authenticated user context)
 * - RFC 7807 error responses
 * - Multi-phase endpoint integration
 */

import { ErrorResponse, DisplayError, PaginatedResponse, OrderRow } from '@/types';
import { tokenManager } from './TokenManager';

/**
 * User profile from authentication
 */
export interface UserProfile {
  id: string;
  username: string;
  email: string;
  name: string;
  role: 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN';
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
export type ResponseInterceptor = (response: Response) => Response | Promise<Response>;

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

  constructor(baseURL?: string) {
    this.baseURL = baseURL || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
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
          username: decodedToken.username || '',
          email: decodedToken.email || '',
          name: decodedToken.name || decodedToken.username || '',
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
   * Get authorization token from TokenManager
   * @deprecated Use tokenManager.get() directly
   */
  private getAuthToken(): string | null {
    return tokenManager.get();
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
    } else if (response.status === 0 || response.type === 'basic') {
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
        response = await interceptor(response);
      }

      // Parse response body
      let data: unknown;
      const contentType = response.headers.get('content-type');
      if (contentType?.includes('application/json')) {
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
  // PHASE 1: ORDER MANAGEMENT ENDPOINTS
  // ============================================================================

  /**
   * Get order list with pagination and filtering
   * GET /api/orders?page=1&pageSize=25&status=CREATED,READY&sortBy=created&sortOrder=desc
   */
  async getOrders(params?: {
    page?: number;
    pageSize?: number;
    status?: string[];
    sortBy?: 'eta' | 'status' | 'created' | 'customerName' | 'orderId';
    sortOrder?: 'asc' | 'desc';
    search?: string;
  }): Promise<PaginatedResponse<OrderRow>> {
    const queryParams = new URLSearchParams();

    if (params?.page) queryParams.append('page', params.page.toString());
    if (params?.pageSize) queryParams.append('pageSize', params.pageSize.toString());
    if (params?.status?.length) {
      params.status.forEach(status => queryParams.append('status', status));
    }
    if (params?.sortBy) queryParams.append('sortBy', params.sortBy);
    if (params?.sortOrder) queryParams.append('sortOrder', params.sortOrder);
    if (params?.search) queryParams.append('search', params.search);

    const queryString = queryParams.toString();
    const endpoint = `/api/orders${queryString ? '?' + queryString : ''}`;

    return this.get<PaginatedResponse<OrderRow>>(endpoint);
  }

  /**
   * Create a new order
   * POST /api/orders
   */
  async createOrder(data: {
    customerSessionId: string;
    items: Array<{ menuItemId: string; quantity: number; specialInstructions?: string }>;
  }): Promise<{ id: string; orderId: string; status: string; createdAt: string }> {
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
    status: 'IN_PROGRESS' | 'READY' | 'COMPLETED' | 'CANCELLED'
  ): Promise<{ id: string; status: string; updatedAt: string }> {
    return this.patch(`/api/orders/${orderId}/status`, { status });
  }

  /**
   * Setup EventSource for real-time order updates
   * GET /api/orders/stream (Server-Sent Events)
   * 
   * Events:
   * - OrderCreated
   * - OrderStatusUpdated
   * - OrderReady
   */
  setupSSE(
    onMessage: (event: OrderUpdateEvent) => void,
    onError: (error: Error) => void
  ): () => void {
    // Note: Token authentication for EventSource requires backend support for token in URL or CORS headers
    // Use fetch-based SSE instead of EventSource API for better control
    const token = this.getAuthToken();
    if (!token) {
      onError(new Error('No authentication token available'));
      return () => {};
    }

    // Use fetch with ReadableStream for SSE with Bearer token support
    const controller = new AbortController();

    const startSSE = async () => {
      try {
        const response = await fetch(`${this.baseURL}/api/orders/stream`, {
          headers: this.getHeaders(),
          signal: controller.signal,
        });

        if (!response.ok) {
          throw new Error(`SSE connection failed: ${response.status}`);
        }

        const reader = response.body?.getReader();
        if (!reader) {
          throw new Error('Failed to get response reader');
        }

        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');

          // Process complete lines
          for (let i = 0; i < lines.length - 1; i++) {
            const line = lines[i].trim();

            if (line.startsWith('event:')) {
              const eventType = line.slice(6).trim();
              const dataLine = lines[i + 1];
              if (dataLine?.startsWith('data:')) {
                const dataStr = dataLine.slice(5).trim();
                try {
                  const data = JSON.parse(dataStr);
                  onMessage({
                    type: eventType as 'OrderCreated' | 'OrderStatusUpdated' | 'OrderReady',
                    ...data,
                  });
                } catch (error) {
                  console.error('Failed to parse SSE data:', error);
                }
              }
              i++; // Skip the data line
            }
          }

          // Keep incomplete line in buffer
          buffer = lines[lines.length - 1];
        }
      } catch (error) {
        if (error instanceof Error && error.name !== 'AbortError') {
          onError(error);
        }
      }
    };

    startSSE();

    // Return cleanup function
    return () => controller.abort();
  }

  /**
   * Polling fallback: Get orders updated since last fetch
   * GET /api/orders/poll?since=2024-01-15T10:00:00Z&eTag=abc123
   * 
   * Returns 304 Not Modified if ETag matches (no change)
   */
  async pollOrders(params?: {
    since?: Date;
    eTag?: string;
  }): Promise<{ orders: OrderRow[]; eTag: string }> {
    const queryParams = new URLSearchParams();
    if (params?.since) queryParams.append('since', params.since.toISOString());
    if (params?.eTag) queryParams.append('eTag', params.eTag);

    const queryString = queryParams.toString();
    const endpoint = `/api/orders/poll${queryString ? '?' + queryString : ''}`;

    return this.get(endpoint);
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
      localStorage.setItem('auth-token', typedResponse.token);
      
      const userProfile: UserProfile = {
        id: typedResponse.username || '',
        username: typedResponse.username || '',
        email: typedResponse.email || '',
        name: typedResponse.name || typedResponse.username || '',
        role: typedResponse.role as 'STAFF' | 'ADMIN' | 'PLATFORM_ADMIN',
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
  // PHASE 2: RESTAURANT STAFF ADMIN ENDPOINTS
  // ============================================================================

  /**
   * Create staff account
   * POST /api/admin/staff (ADMIN role required)
   */
  async createStaffAccount(data: {
    username: string;
    email: string;
    password: string;
    name: string;
    role?: 'STAFF' | 'ADMIN';
  }): Promise<{
    id: string;
    username: string;
    email: string;
    name: string;
    role: string;
    status: string;
    createdAt: string;
  }> {
    return this.post('/api/admin/staff', data);
  }

  /**
   * Deactivate staff account
   * DELETE /api/admin/staff/{accountId} (ADMIN role required)
   */
  async deactivateStaffAccount(accountId: string): Promise<void> {
    await this.delete(`/api/admin/staff/${accountId}`);
  }

  /**
   * Update restaurant configuration
   * PUT /api/admin/config (ADMIN role required)
   */
  async updateRestaurantConfig(data: {
    restaurantName: string;
    phoneNumber?: string;
    address?: string;
    cuisineType?: string;
    averagePrepTime?: number;
    webhookUrl?: string;
  }): Promise<{
    id: string;
    restaurantName: string;
    phoneNumber?: string;
    address?: string;
    cuisineType?: string;
    averagePrepTime?: number;
    webhookUrl?: string;
    updatedAt: string;
  }> {
    return this.put('/api/admin/config', data);
  }

  // ============================================================================
  // PHASE 3: PLATFORM ADMIN ENDPOINTS
  // ============================================================================

  /**
   * Create restaurant tenant
   * POST /api/platform/restaurants (PLATFORM_ADMIN role required)
   */
  async createRestaurant(data: {
    restaurantName: string;
    ownerEmail: string;
    ownerName: string;
  }): Promise<{
    tenantId: string;
    restaurantName: string;
    status: string;
    createdAt: string;
  }> {
    return this.post('/api/platform/restaurants', data);
  }

  /**
   * Update restaurant (Platform Admin)
   * PUT /api/platform/restaurants/{tenantId} (PLATFORM_ADMIN role required)
   */
  async updateRestaurant(
    tenantId: string,
    data: {
      restaurantName?: string;
      status?: 'ACTIVE' | 'INACTIVE';
    }
  ): Promise<{
    tenantId: string;
    restaurantName: string;
    status: string;
    updatedAt: string;
  }> {
    return this.put(`/api/platform/restaurants/${tenantId}`, data);
  }

  /**
   * Deactivate restaurant
   * DELETE /api/platform/restaurants/{tenantId} (PLATFORM_ADMIN role required)
   */
  async deactivateRestaurant(tenantId: string): Promise<void> {
    await this.delete(`/api/platform/restaurants/${tenantId}`);
  }

  // ============================================================================
  // PHASE 4: CUSTOMER SESSION ENDPOINTS (PWA - FUTURE)
  // ============================================================================

  /**
   * Start customer session from QR code
   * POST /api/sessions (public, no authentication required)
   */
  async startCustomerSession(qrToken: string): Promise<{
    sessionId: string;
    tenantId: string;
    restaurantName: string;
    status: string;
    createdAt: string;
    expiresAt: string;
  }> {
    return this.post('/api/sessions', { qrToken });
  }

  /**
   * Get customer session orders
   * GET /api/sessions/{sessionId}/orders (public, no authentication required)
   */
  async getCustomerSessionOrders(sessionId: string): Promise<{
    sessionId: string;
    orders: Array<{
      id: string;
      orderId: string;
      status: string;
      items: Array<{ name: string; quantity: number }>;
      createdAt: string;
      estimatedReadyTime: string;
    }>;
  }> {
    return this.get(`/api/sessions/${sessionId}/orders`);
  }

  /**
   * Register device for push notifications
   * POST /api/sessions/{sessionId}/subscriptions (public)
   */
  async registerDeviceSubscription(
    sessionId: string,
    subscription: {
      endpoint: string;
      keys: {
        p256dh: string;
        auth: string;
      };
    }
  ): Promise<{
    subscriptionId: string;
    endpoint: string;
    createdAt: string;
  }> {
    return this.post(`/api/sessions/${sessionId}/subscriptions`, subscription);
  }

  /**
   * Revoke device subscription
   * DELETE /api/sessions/{sessionId}/subscriptions/{subscriptionId} (public)
   */
  async revokeDeviceSubscription(sessionId: string, subscriptionId: string): Promise<void> {
    await this.delete(`/api/sessions/${sessionId}/subscriptions/${subscriptionId}`);
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
 * Order Update Event from SSE stream
 */
export interface OrderUpdateEvent {
  type: 'OrderStatusUpdated' | 'OrderCreated' | 'OrderReady';
  orderId?: string;
  status?: string;
  updatedAt?: Date | string;
  createdAt?: Date | string;
  customerName?: string;
  previousStatus?: string;
}

/**
 * Create singleton instance
 */
export const apiClient = new ApiClient();
