/**
 * Core Types and Interfaces for Dashboard UI
 * Aligned with Backend API contracts from ALIGNMENT.md
 */

import React from 'react';

/* ============================================================================
   THEME & UI TYPES
   ============================================================================ */

/** Theme type - light or dark mode */
export type Theme = 'light' | 'dark';

/**
 * Theme Context Type
 * Provides theme state and control functions to components
 */
export interface ThemeContextType {
  theme: Theme;
  toggleTheme: () => void;
  setTheme: (theme: Theme) => void;
  currentMode: Theme;
}

/* ============================================================================
   ORDER MANAGEMENT TYPES (BACKEND ALIGNED)
   ============================================================================ */

/**
 * OrderStatus - Backend enum values (exactly matching backend)
 * From ALIGNMENT.md: Backend defines OrderStatus as enum
 */
export type OrderStatus = 'CREATED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED';

/**
 * Order Entity - Core order data from backend
 * Maps to backend Order entity
 */
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

/**
 * OrderItem - Line item in an order
 */
export interface OrderItem {
  id: string;
  menuItemId: string;
  name: string;
  quantity: number;
  specialInstructions?: string;
  price: number;
}

/**
 * OrderRow - Display model for table rows
 * Maps to backend Order response, formatted for UI
 */
export interface OrderRow {
  id: string;                           // Backend Order.id
  orderId: string;                      // Backend Order.orderId (display)
  customerName?: string;                // Derived from context
  itemsOrdered: string;                 // Formatted from items
  deliveryETA: Date;                    // Backend Order.estimatedReadyAt
  status: OrderStatus;                  // Backend Order.status
  estimatedWaitTime: number;            // Calculated: (ETA - now) in minutes
  itemCount: number;                    // items.length
}

/**
 * OrderMetrics - Aggregated order statistics
 * Calculated by frontend from order list (not fetched endpoint)
 */
export interface OrderMetrics {
  totalOrders: number;          // Sum of all orders
  newOrders: number;            // Count where status === 'CREATED'
  preparing: number;            // Count where status === 'PREPARING'
  readyForPickup: number;       // Count where status === 'READY'
  completed: number;            // Count where status === 'COMPLETED'
  cancelled?: number;           // Count where status === 'CANCELLED'
}

/**
 * Trend - Metric trend indicator (up/down with percentage)
 */
export interface Trend {
  value: number;                // Percentage (e.g., 12.5)
  direction: 'up' | 'down';
  label: string;                // e.g., "vs. last hour"
}

/**
 * OrderFilters - Query parameters for order listing
 */
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

/* ============================================================================
   USER MANAGEMENT TYPES
   ============================================================================ */

/**
 * UserProfile - Staff or admin user information
 */
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

/* ============================================================================
   MENU TYPES
   ============================================================================ */

/**
 * MenuItem - Menu item available for ordering
 */
export interface MenuItem {
  id: string;
  name: string;
  price: number;
  description?: string;
  category: string;
  available: boolean;
  imageUrl?: string;
}

/* ============================================================================
   API RESPONSE TYPES
   ============================================================================ */

/**
 * Generic API response wrapper
 */
export interface ApiResponse<T> {
  data: T;
  status: number;
  message?: string;
}

/**
 * Paginated API response (backend format)
 * From backend endpoints GET /api/orders
 */
export interface PaginatedResponse<T> {
  items: T[];
  totalCount: number;
  pageCount: number;
  currentPage: number;
}

/**
 * ErrorResponse - RFC 7807 Problem+JSON format (backend error format)
 * From ALIGNMENT.md: Backend returns RFC 7807 problem+json
 */
export interface ErrorResponse {
  type: string;                 // e.g., "urn:readybell:orders:invalid-transition"
  title: string;                // e.g., "Invalid State Transition"
  status: number;               // HTTP status code
  detail: string;               // Human-readable detail
  instance?: string;            // Request URI
  code?: string;                // Application-specific error code
}

/**
 * Frontend error display format (mapped from ErrorResponse)
 */
export interface DisplayError {
  type: 'network' | 'auth' | 'validation' | 'server' | 'unknown';
  message: string;
  code?: string;
  retryable: boolean;
  timestamp: Date;
}

/* ============================================================================
   UI COMPONENT TYPES
   ============================================================================ */

/**
 * Status Color Configuration - for displaying status badges
 */
export interface OrderStatusConfig {
  label: string;
  color: {
    light: string;              // Light mode hex color
    dark: string;               // Dark mode hex color
  };
  icon?: React.ComponentType<{ className?: string }>;
  priority: number;             // Sort order
}

/**
 * Card Component Props - for summary cards
 */
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

/**
 * Table Column Definition
 */
export interface ColumnDefinition<T> {
  id: string;
  header: string;
  accessor: keyof T;
  sortable?: boolean;
  width?: string | number;
  render?: (value: T[keyof T]) => React.ReactNode;
}
