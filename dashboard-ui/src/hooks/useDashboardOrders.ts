import { useCallback, useEffect, useRef, useState } from 'react';
import { OrderRow } from '@/types';
import { apiClient } from '@/utils/api';
import { tokenManager } from '@/utils/TokenManager';
import { useSSE } from './useSSE';

/** How many of the newest orders the dashboard loads (backend maximum page size). */
export const DASHBOARD_ORDER_LIMIT = 100;

/** Fallback refresh interval while the live connection is down. */
const POLL_INTERVAL_MS = 30_000;

/** Delay that coalesces bursts of SSE events into a single reload. */
const RELOAD_DEBOUNCE_MS = 300;

export interface UseDashboardOrdersState {
  orders: OrderRow[];
  isLoading: boolean;
  /** Last load error, cleared by the next successful load. */
  error: Error | null;
  /** True while the SSE stream is open. */
  isConnected: boolean;
  reload: () => Promise<void>;
}

/**
 * Loads the tenant's newest orders from the backend and keeps them fresh.
 *
 * - Initial load through {@code GET /api/orders}.
 * - {@code GET /api/orders/stream} (SSE) signals changes; each burst triggers one reload, so
 *   rows always carry the server's data (customer, items, ETA) rather than a partial event.
 * - While the stream is down the list is refreshed every 30 s.
 */
export function useDashboardOrders(): UseDashboardOrdersState {
  const [orders, setOrders] = useState<OrderRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const mounted = useRef(true);

  const reload = useCallback(async () => {
    try {
      const page = await apiClient.getOrders({
        page: 1,
        pageSize: DASHBOARD_ORDER_LIMIT,
        sortBy: 'created',
        sortOrder: 'desc',
      });
      if (!mounted.current) return;
      setOrders(page.items);
      setError(null);
    } catch (e) {
      if (!mounted.current) return;
      setError(e instanceof Error ? e : new Error('Failed to load orders'));
    } finally {
      if (mounted.current) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    void reload();
    return () => {
      mounted.current = false;
    };
  }, [reload]);

  const debounce = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onMessage = useCallback(() => {
    if (debounce.current) clearTimeout(debounce.current);
    debounce.current = setTimeout(() => void reload(), RELOAD_DEBOUNCE_MS);
  }, [reload]);

  useEffect(() => () => {
    if (debounce.current) clearTimeout(debounce.current);
  }, []);

  const sse = useSSE({
    endpoint: '/api/orders/stream',
    authToken: tokenManager.get() ?? undefined,
    onMessage,
  });

  useEffect(() => {
    if (sse.isConnected) return undefined;
    const timer = setInterval(() => void reload(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [sse.isConnected, reload]);

  return { orders, isLoading, error, isConnected: sse.isConnected, reload };
}
