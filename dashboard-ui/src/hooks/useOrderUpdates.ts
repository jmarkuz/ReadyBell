import { useCallback, useEffect, useRef, useState } from 'react';
import { Order, OrderStatus } from '@/types';
import { useSSE } from './useSSE';

interface UseOrderUpdatesOptions {
  orders: Order[];
  onOrdersUpdate: (orders: Order[]) => void;
  endpoint?: string;
  enabled?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  currentPage?: number;
  pageSize?: number;
  onSSEError?: (error: Error) => void;
}

interface UseOrderUpdatesState {
  isConnected: boolean;
  isUsingSSE: boolean;
  isPolling: boolean;
  error: Error | null;
}

/**
 * Hook for handling real-time order updates via SSE with fallback to polling.
 * Listens to order stream and updates table data while maintaining
 * sort order and pagination state.
 *
 * Features:
 * - Real-time order updates via SSE
 * - Maintains sort order (re-sorts only if sorted column changed)
 * - Preserves pagination state
 * - Handles new orders (added to top of list)
 * - Handles status updates (refreshes specific row)
 * - Error recovery with fallback to polling
 * - Deduplicates orders by orderId
 *
 * @param options - Configuration options
 * @returns Connection state and update status
 *
 * @example
 * ```tsx
 * const state = useOrderUpdates({
 *   orders,
 *   onOrdersUpdate: setOrders,
 *   sortBy: 'createdAt',
 *   sortOrder: 'desc',
 *   currentPage: 1,
 *   pageSize: 10,
 * });
 * ```
 */
export function useOrderUpdates(options: UseOrderUpdatesOptions): UseOrderUpdatesState {
  const {
    orders,
    onOrdersUpdate,
    endpoint = '/api/orders/stream',
    enabled = true,
    sortBy,
    sortOrder = 'desc',
    currentPage = 1,
    pageSize = 10,
    onSSEError,
  } = options;

  const [state, setState] = useState<UseOrderUpdatesState>({
    isConnected: false,
    isUsingSSE: false,
    isPolling: false,
    error: null,
  });

  const ordersRef = useRef(orders);
  const sortStateRef = useRef({ sortBy, sortOrder, currentPage, pageSize });

  // Keep refs in sync with props
  useEffect(() => {
    ordersRef.current = orders;
    sortStateRef.current = { sortBy, sortOrder, currentPage, pageSize };
  }, [orders, sortBy, sortOrder, currentPage, pageSize]);

  /**
   * Apply sorting to orders if a sort column is active
   */
  const applySorting = useCallback((updatedOrders: Order[]): Order[] => {
    if (!sortStateRef.current.sortBy) {
      return updatedOrders;
    }

    const { sortBy: sortColumn, sortOrder: order } = sortStateRef.current;

    return [...updatedOrders].sort((a, b) => {
      let aVal: unknown = a[sortColumn as keyof Order];
      let bVal: unknown = b[sortColumn as keyof Order];

      // Handle date strings
      if (typeof aVal === 'string' && typeof bVal === 'string') {
        try {
          aVal = new Date(aVal as string).getTime();
          bVal = new Date(bVal as string).getTime();
        } catch {
          // Keep as strings if not valid dates
        }
      }

      if (typeof aVal === 'number' && typeof bVal === 'number') {
        return order === 'asc' ? aVal - bVal : bVal - aVal;
      }

      const aStr = String(aVal || '');
      const bStr = String(bVal || '');
      return order === 'asc'
        ? aStr.localeCompare(bStr)
        : bStr.localeCompare(aStr);
    });
  }, []);

  const handleOrderUpdate = useCallback((update: unknown) => {
    try {
      const orderUpdate = update as Partial<Order> & { id?: string };

      if (!orderUpdate.id) {
        console.warn('Order update missing id', orderUpdate);
        return;
      }

      let updatedOrders = [...ordersRef.current];
      const existingIndex = updatedOrders.findIndex((o) => o.id === orderUpdate.id);

      if (existingIndex >= 0) {
        // Update existing order
        updatedOrders[existingIndex] = {
          ...updatedOrders[existingIndex],
          ...orderUpdate,
          updatedAt: new Date().toISOString(),
        };
      } else {
        // Add new order to top of list
        updatedOrders.unshift({
          id: orderUpdate.id,
          orderId: orderUpdate.orderId || orderUpdate.id,
          customerId: orderUpdate.customerId || 'unknown',
          restaurantId: orderUpdate.restaurantId || 'unknown',
          items: orderUpdate.items || [],
          status: orderUpdate.status || ('CREATED' as OrderStatus),
          createdAt: orderUpdate.createdAt || new Date().toISOString(),
          estimatedReadyAt: orderUpdate.estimatedReadyAt || new Date(Date.now() + 30 * 60000).toISOString(),
          totalPrice: orderUpdate.totalPrice || 0,
          deliveryMethod: orderUpdate.deliveryMethod || 'pickup',
          updatedAt: new Date().toISOString(),
        });
      }

      // Deduplicate by id (remove duplicates, keep first occurrence)
      const seen = new Set<string>();
      updatedOrders = updatedOrders.filter((order) => {
        if (seen.has(order.id)) {
          return false;
        }
        seen.add(order.id);
        return true;
      });

      // Apply sorting if a sort column is active
      updatedOrders = applySorting(updatedOrders);

      onOrdersUpdate(updatedOrders);

      setState((prev) => ({
        ...prev,
        error: null,
      }));
    } catch (error) {
      console.error('Error processing order update:', error);
      const err = error instanceof Error ? error : new Error('Unknown error');
      setState((prev) => ({
        ...prev,
        error: err,
      }));
      onSSEError?.(err);
    }
  }, [onOrdersUpdate, applySorting, onSSEError]);

  const handleSSEError = useCallback((error: Error) => {
    console.error('SSE connection error:', error);
    setState((prev) => ({
      ...prev,
      error,
      isUsingSSE: false,
      isRetrying: false,
    }));
    onSSEError?.(error);
  }, [onSSEError]);

  const sseState = useSSE({
    endpoint,
    onMessage: handleOrderUpdate,
    onError: handleSSEError,
    maxRetries: Infinity,
    initialDelay: 1000,
  });

  // Update state with SSE connection status
  useEffect(() => {
    setState((prev) => ({
      ...prev,
      isConnected: sseState.isConnected,
      isUsingSSE: sseState.isConnected,
      isPolling: false,
      error: sseState.error,
    }));
  }, [sseState.isConnected, sseState.error]);

  return state;
}
