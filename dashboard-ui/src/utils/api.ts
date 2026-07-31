/**
 * API Client Configuration
 * Handles all HTTP requests to the backend with authentication, interceptors, and error handling
 * 
 * Base URL: configurable via VITE_API_BASE_URL env var or defaults to http://localhost:8080
 */

import { ErrorResponse, DisplayError, PaginatedResponse, OrderRow } from '@/types';

/**
 * API Client Class
 * Handles HTTP requests, authentication, error handling
 */
export class ApiClient {
  private baseURL: string;
  private defaultHeaders: HeadersInit;

  constructor(baseURL?: string) {
    this.baseURL = baseURL || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
    this.defaultHeaders = {
      'Content-Type': 'application/json',
    };
  }

  /**
   * Get authorization token from localStorage
   */
  private getAuthToken(): string | null {
    try {
      return localStorage.getItem('auth-token');
    } catch (error) {
      console.warn('Failed to read auth token from localStorage:', error);
      return null;
    }
  }

  /**
   * Build request headers with auth token if available
   */
  private getHeaders(): HeadersInit {
    const headers = { ...this.defaultHeaders };
    const token = this.getAuthToken();
    if (token) {
      (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  }

  /**
   * Map backend error response to frontend display error
   */
  private mapError(response: Response, data: unknown): DisplayError {
    let errorData: Partial<ErrorResponse> = {};
    
    if (data && typeof data === 'object' && 'type' in data) {
      errorData = data as Partial<ErrorResponse>;
    }

    // Determine error type based on HTTP status
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
   * Perform HTTP request with error handling
   */
  private async request<T>(
    method: string,
    endpoint: string,
    body?: unknown
  ): Promise<T> {
    const url = `${this.baseURL}${endpoint}`;

    try {
      const options: RequestInit = {
        method,
        headers: this.getHeaders(),
      };

      if (body) {
        options.body = JSON.stringify(body);
      }

      const response = await fetch(url, options);

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
      // Handle network errors or propagate API errors
      if (error instanceof ApiError) {
        throw error;
      }

      if (error instanceof TypeError && error.message.includes('fetch')) {
        const displayError: DisplayError = {
          type: 'network',
          message: 'Network error. Check your connection and try again.',
          retryable: true,
          timestamp: new Date(),
        };
        throw new ApiError(displayError.message, 0, displayError);
      }

      throw error;
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

  /**
   * Get order list with pagination and filtering
   * GET /api/orders?page=1&pageSize=25&status=CREATED,READY&sortBy=created&sortOrder=desc
   */
  async getOrders(params?: {
    page?: number;
    pageSize?: number;
    status?: string[];
    sortBy?: 'eta' | 'status' | 'created';
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
    items: Array<{ menuItemId: string; quantity: number }>;
    specialInstructions?: string;
  }): Promise<{ id: string; orderId: string }> {
    return this.post('/api/orders', data);
  }

  /**
   * Update order status
   * PATCH /api/orders/{id}/status
   */
  async updateOrderStatus(orderId: string, status: string): Promise<{ id: string; status: string; updatedAt: string }> {
    return this.patch(`/api/orders/${orderId}/status`, { status });
  }

  /**
   * Setup EventSource for real-time updates
   * GET /api/orders/stream (Server-Sent Events)
   */
  setupSSE(onMessage: (event: OrderUpdateEvent) => void, onError: (error: Error) => void): () => void {
    // Note: Token authentication for EventSource requires backend support for token in URL or CORS headers
    // This is a limitation of the EventSource API which doesn't support custom headers
    // In production, consider using fetch with ReadableStream instead
    
    // Create SSE connection (EventSource doesn't support custom headers directly)
    const source = new EventSource(
      `${this.baseURL}/api/orders/stream`,
      { withCredentials: true }
    );

    source.addEventListener('OrderStatusUpdated', (event: Event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        onMessage({
          type: 'OrderStatusUpdated',
          orderId: data.orderId,
          status: data.status,
          updatedAt: new Date(data.updatedAt),
        });
      } catch (error) {
        console.error('Failed to parse SSE message:', error);
      }
    });

    source.addEventListener('OrderCreated', (event: Event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        onMessage({
          type: 'OrderCreated',
          orderId: data.orderId,
          status: data.status,
          createdAt: new Date(data.createdAt),
        });
      } catch (error) {
        console.error('Failed to parse SSE message:', error);
      }
    });

    source.addEventListener('error', () => {
      if (source.readyState === EventSource.CLOSED) {
        onError(new Error('SSE connection closed'));
        source.close();
      } else if (source.readyState === EventSource.CONNECTING) {
        console.warn('SSE connection error, retrying...');
      }
    });

    // Return cleanup function
    return () => source.close();
  }

  /**
   * Polling fallback: Get orders updated since last fetch
   * GET /api/orders/poll?since=2024-01-15T10:00:00Z&eTag=abc123
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
  type: 'OrderStatusUpdated' | 'OrderCreated';
  orderId: string;
  status?: string;
  updatedAt?: Date;
  createdAt?: Date;
}

/**
 * Create singleton instance
 */
export const apiClient = new ApiClient();
