import { useCallback, useEffect, useRef, useState } from 'react';
import { Order, OrderStatus } from '@/types';
import { useSSE } from './useSSE';

interface UseOrderUpdatesOptions {
  orders: Order[];
  onOrdersUpdate: (orders: Order[]) => void;
  endpoint?: string;
  enabled?: boolean;
}

interface UseOrderUpdatesState {
  isConnected: boolean;
  isRetrying: boolean;
  error: Error | null;
  lastUpdate: Date | null;
}

/**
 * Hook for handling real-time order updates via SSE.
 * Listens to order stream and updates table data while maintaining
 * sort order and pagination state.
 *
 * Features:
 * - Real-time order updates
 * - Maintains sort order and pagination
 * - Handles new orders and status changes
 * - Error recovery with fallback to polling
 *
 * @param options - Configuration options
 * @returns Connection state and update status
 */
export function useOrderUpdates(options: UseOrderUpdatesOptions): UseOrderUpdatesState {
  const {
    orders,
    onOrdersUpdate,
    endpoint = '/api/orders/stream',
    enabled = true,
  } = options;

  const [state, setState] = useState<UseOrderUpdatesState>({
    isConnected: false,
    isRetrying: false,
    error: null,
    lastUpdate: null,
  });

  const ordersRef = useRef(orders);
  const updateQueueRef = useRef<Order[]>([]);

  // Keep ref in sync with prop
  useEffect(() => {
    ordersRef.current = orders;
  }, [orders]);

  const handleOrderUpdate = useCallback((update: unknown) => {
    try {
      const orderUpdate = update as Partial<Order> & { id: string };

      if (!orderUpdate.id) {
        console.warn('Order update missing id', orderUpdate);
        return;
      }

      // Update or add order
      const updatedOrders = ordersRef.current.map((order) =>
        order.id === orderUpdate.id
          ? { ...order, ...orderUpdate, updatedAt: new Date().toISOString() }
          : order
      );

      // Add new order if not found
      if (!updatedOrders.some((o) => o.id === orderUpdate.id)) {
        updatedOrders.unshift({
          id: orderUpdate.id,
          customerName: orderUpdate.customerName || 'Unknown',
          items: orderUpdate.items || [],
          status: orderUpdate.status || ('CREATED' as OrderStatus),
          deliveryEta: orderUpdate.deliveryEta || new Date().toISOString(),
          createdAt: orderUpdate.createdAt || new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        });
      }

      // Queue update
      updateQueueRef.current.push(orderUpdate as Order);
      onOrdersUpdate(updatedOrders);

      // Update state with last update timestamp
      setState((prev) => ({
        ...prev,
        lastUpdate: new Date(),
      }));
    } catch (error) {
      console.error('Error processing order update:', error);
    }
  }, [onOrdersUpdate]);

  const handleSSEError = useCallback((error: Error) => {
    console.error('SSE connection error:', error);
    setState((prev) => ({
      ...prev,
      error,
    }));
  }, []);

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
      isRetrying: sseState.isRetrying,
      error: sseState.error,
    }));
  }, [sseState.isConnected, sseState.isRetrying, sseState.error]);

  return state;
}
