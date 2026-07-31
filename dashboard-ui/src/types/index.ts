/**
 * Core Types and Interfaces for Dashboard UI
 */

/** Order Status Type */
export type OrderStatus = 'CREATED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED';

export type Theme = 'light' | 'dark';

/**
 * Order Entity
 */
export interface Order {
  id: string;
  orderId: string;
  customerId: string;
  restaurantId: string;
  items: OrderItem[];
  status: OrderStatus;
  createdAt: Date;
  estimatedReadyAt: Date;
  completedAt?: Date;
  specialInstructions?: string;
  totalPrice: number;
  deliveryMethod: 'pickup' | 'delivery';
}

export interface OrderItem {
  id: string;
  menuItemId: string;
  name: string;
  quantity: number;
  specialInstructions?: string;
  price: number;
}

/**
 * Order Row for Table Display
 */
export interface OrderRow {
  id: string;
  orderId: string;
  customerName?: string;
  itemsOrdered: string;
  deliveryETA: Date;
  status: OrderStatus;
  estimatedWaitTime: number;
  itemCount: number;
}

/**
 * Order Metrics
 */
export interface OrderMetrics {
  totalOrders: number;
  newOrders: number;
  preparing: number;
  readyForPickup: number;
  completed: number;
  cancelled?: number;
}

/**
 * Trend Data
 */
export interface Trend {
  value: number;
  direction: 'up' | 'down';
  label: string;
}

/**
 * User Profile
 */
export interface UserProfile {
  id: string;
  name: string;
  email: string;
  role: string;
  avatarUrl?: string;
  restaurantName: string;
}

/**
 * Menu Item
 */
export interface MenuItem {
  id: string;
  name: string;
  price: number;
  description?: string;
  category: string;
  available: boolean;
}

/**
 * Filters
 */
export interface OrderFilters {
  status?: OrderStatus[];
  dateRange?: {
    start: Date;
    end: Date;
  };
  searchQuery?: string;
}

/**
 * API Response Types
 */
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

/**
 * Theme Context Type
 */
export interface ThemeContextType {
  theme: Theme;
  toggleTheme: () => void;
  setTheme: (theme: Theme) => void;
  currentMode: Theme;
}

/**
 * Status Color Configuration
 */
export interface OrderStatusConfig {
  label: string;
  color: {
    light: string;
    dark: string;
  };
  icon?: React.ComponentType<{ className?: string }>;
  priority: number;
}
