
import React from 'react';

export type {
  ProblemDetail,
  ErrorContext,
  ParsedError,
} from './errors';
export {
  ERROR_MESSAGES,
  parseProblemDetail,
  extractProblemDetail,
  createParsedError,
  extractErrorContext,
  mapErrorToUserMessage,
  mapStatusToErrorType,
  isRetryable,
  mapStatusToSeverity,
  logError,
  ApiError,
  handleErrorResponse,
} from './errors';

export type Theme = 'light' | 'dark';

export interface ThemeContextType {
  theme: Theme;
  toggleTheme: () => void;
  setTheme: (theme: Theme) => void;
  currentMode: Theme;
}

/** Order status as the UI names it: the backend's IN_PROGRESS is PREPARING here. */
export type OrderStatus = 'CREATED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED';

export interface Order {
  id: string;                           // UUID
  orderId: string;                      // Display ID
  customerId: string;
  restaurantId: string;
  items: OrderItem[];
  status: OrderStatus;                  // Backend enum value
  createdAt: Date;
  estimatedReadyAt: Date;
  completedAt?: Date;
  specialInstructions?: string;
  totalPrice: number;
  deliveryMethod: 'pickup' | 'delivery';
  version?: number;                     // Optimistic locking version
}

/** OrderItem - Line item in an order */
export interface OrderItem {
  id: string;
  menuItemId: string;
  name: string;
  quantity: number;
  specialInstructions?: string;
  price: number;
}

/** An order as a table row, formatted for the UI. */
export interface OrderRow {
  id: string;                           // Backend Order.id
  orderId: string;                      // Backend Order.orderId (display)
  customerName?: string;                // Derived from context
  itemsOrdered: string;                 // Formatted from items
  deliveryETA: Date | null;             // Backend Order.estimatedReadyAt (null when staff gave no ETA)
  status: OrderStatus;                  // Backend Order.status
  estimatedWaitTime: number | null;     // (ETA - now) in minutes, null without ETA
  itemCount: number;                    // items.length
  sessionId?: string;                   // staff only: the customer session whose tracking link the QR code encodes
  connected?: boolean;                  // true once the customer opened that link (scanned the QR)
}

/**
 * OrderMetrics - Aggregated order statistics for the dashboard cards.
 * Real totals for the whole restaurant from GET /api/orders/metrics, independent of table filters and paging.
 */
export interface OrderMetrics {
  totalOrders: number;          // Sum of all orders
  newOrders: number;            // Count where status === 'CREATED'
  preparing: number;            // Count where status === 'PREPARING'
  readyForPickup: number;       // Count where status === 'READY'
  completed: number;            // Count where status === 'COMPLETED'
  cancelled?: number;           // Count where status === 'CANCELLED'
}

/** Trend - Metric trend indicator (up/down with percentage) */
export interface Trend {
  value: number;                // Percentage (e.g., 12.5)
  direction: 'up' | 'down';
  label: string;                // e.g., "vs. last hour"
}

/** OrderFilters - Query parameters for order listing */
export interface OrderFilters {
  status?: OrderStatus[];
  dateRange?: {
    start: Date;
    end: Date;
  };
  searchQuery?: string;
  page?: number;
  pageSize?: number;
  sortBy?: 'eta' | 'status' | 'created';
  sortOrder?: 'asc' | 'desc';
}

/** UserProfile - Staff or admin user information */
export interface UserProfile {
  id: string;
  name: string;
  email: string;
  role: 'STAFF' | 'RESTAURANT_ADMIN' | 'PLATFORM_ADMIN';
  avatarUrl?: string;
  restaurantName: string;
  restaurantId?: string;
  tenantId?: string;
}

/** MenuItem - Menu item available for ordering */
export interface MenuItem {
  id: string;
  name: string;
  price: number;
  description?: string;
  category: string;
  available: boolean;
  imageUrl?: string;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  message?: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  totalCount: number;
  pageCount: number;
  currentPage: number;
}

/** RFC 7807 problem+json, the backend's error format. */
export interface ErrorResponse {
  type: string;                 // e.g., "urn:readybell:orders:invalid-transition"
  title: string;                // e.g., "Invalid State Transition"
  status: number;               // HTTP status code
  detail: string;               // Human-readable detail
  instance?: string;            // Request URI
  code?: string;                // Application-specific error code
}

/** Frontend error display format (mapped from ErrorResponse) */
export interface DisplayError {
  type: 'network' | 'auth' | 'validation' | 'server' | 'unknown';
  message: string;
  code?: string;
  retryable: boolean;
  timestamp: Date;
}

/** Status Color Configuration - for displaying status badges */
export interface OrderStatusConfig {
  label: string;
  color: {
    light: string;              // Light mode hex color
    dark: string;               // Dark mode hex color
  };
  icon?: React.ComponentType<{ className?: string }>;
  priority: number;             // Sort order
}

/** Card Component Props - for summary cards */
export interface CardProps {
  title: string;
  value: number | string;
  icon?: React.ComponentType<{ className?: string }>;
  trend?: Trend;
  backgroundColor?: string;
  onClick?: () => void;
  isLoading?: boolean;
  className?: string;
}

export interface ColumnDefinition<T> {
  id: string;
  header: string;
  accessor: keyof T;
  sortable?: boolean;
  width?: string | number;
  render?: (value: T[keyof T]) => React.ReactNode;
}
