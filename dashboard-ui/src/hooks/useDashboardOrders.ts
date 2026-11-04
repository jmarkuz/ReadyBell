import { useCallback, useEffect, useRef, useState } from 'react';
import { OrderMetrics, OrderRow, OrderStatus } from '@/types';
import { apiClient } from '@/utils/api';
import { tokenManager } from '@/utils/TokenManager';
import { useSSE } from './useSSE';

/** Fallback refresh interval while the live connection is down. */
const POLL_INTERVAL_MS = 30_000;

/** Delay that coalesces bursts of SSE events into a single reload. */
const RELOAD_DEBOUNCE_MS = 300;

export type OrdersSortBy = 'created' | 'eta' | 'status' | 'customerName' | 'orderId';

/** Everything the server needs to produce one page of the Incoming Orders table. */
export interface OrdersQuery {
  /** 1-based page number. */
  page: number;
  pageSize: number;
  /** Empty means all statuses. */
  statuses: OrderStatus[];
  /** Free text matched against order number, customer and item names. */
  search: string;
  /** Orders created at or after this instant. */
  from: Date | null;
  /** Orders created at or before this instant. */
  to: Date | null;
  sortBy: OrdersSortBy;
  sortOrder: 'asc' | 'desc';
}

export const DEFAULT_ORDERS_QUERY: OrdersQuery = {
  page: 1,
  pageSize: 10,
  statuses: [],
  search: '',
  from: null,
  to: null,
  sortBy: 'created',
  sortOrder: 'desc',
};

export interface UseDashboardOrdersState {
  /** The rows of the requested page. */
  orders: OrderRow[];
  /** Orders matching the filters across all pages. */
  totalCount: number;
  pageCount: number;
  /** Real per-status totals for the restaurant, independent of filters and paging (null until loaded). */
  metrics: OrderMetrics | null;
  /** True only until the first page has loaded; later queries keep showing the previous rows. */
  isLoading: boolean;
  /** Last load error, cleared by the next successful load. */
  error: Error | null;
  /** True while the SSE stream is open. */
  isConnected: boolean;
  /** Reloads the current page (and the metrics). */
  reload: () => Promise<void>;
}

const toParams = (q: OrdersQuery) => ({
  page: q.page,
  pageSize: q.pageSize,
  sortBy: q.sortBy,
  sortOrder: q.sortOrder,
  ...(q.statuses.length > 0 && { status: q.statuses }),
  ...(q.search.trim() && { search: q.search.trim() }),
  ...(q.from && { from: q.from.toISOString() }),
  ...(q.to && { to: q.to.toISOString() }),
});

/**
 * Loads one page of the tenant's orders from the backend and keeps it fresh.
 *
 * - {@code GET /api/orders} with the page, filters and sort from {@code query}; any change to
 *   the query is exactly one request, and a response that arrives after a newer request is dropped.
 * - {@code GET /api/orders/stream} (SSE) signals changes; each burst reloads the current page, so
 *   rows always carry the server's data (customer, items, ETA) rather than a partial event.
 * - While the stream is down the page is refreshed every 30 s.
 */
export function useDashboardOrders(query: OrdersQuery): UseDashboardOrdersState {
  const [orders, setOrders] = useState<OrderRow[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [pageCount, setPageCount] = useState(0);
  const [metrics, setMetrics] = useState<OrderMetrics | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const mounted = useRef(true);

  // The latest query for callbacks that must stay stable (SSE, polling timer)
  const queryRef = useRef(query);
  useEffect(() => {
    queryRef.current = query;
  });
  const queryKey = JSON.stringify([
    query.page, query.pageSize, query.statuses, query.search.trim(),
    query.from?.getTime() ?? null, query.to?.getTime() ?? null, query.sortBy, query.sortOrder,
  ]);

  const latestRequest = useRef(0);
  const loadPage = useCallback(async () => {
    const requestId = ++latestRequest.current;
    try {
      const result = await apiClient.getOrders(toParams(queryRef.current));
      if (!mounted.current || requestId !== latestRequest.current) return;
      setOrders(result.items);
      setTotalCount(result.totalCount);
      setPageCount(result.pageCount);
      setError(null);
    } catch (e) {
      if (!mounted.current || requestId !== latestRequest.current) return;
      setError(e instanceof Error ? e : new Error('Failed to load orders'));
    } finally {
      if (mounted.current && requestId === latestRequest.current) setIsLoading(false);
    }
  }, []);

  const loadMetrics = useCallback(async () => {
    try {
      const result = await apiClient.getOrderMetrics();
      if (mounted.current) setMetrics(result);
    } catch {
      // The table reports load errors; the metric cards just keep their last numbers
    }
  }, []);

  const reload = useCallback(async () => {
    await Promise.all([loadPage(), loadMetrics()]);
  }, [loadPage, loadMetrics]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // A new page, filter or sort is one request
  useEffect(() => {
    void loadPage();
  }, [queryKey, loadPage]);

  useEffect(() => {
    void loadMetrics();
  }, [loadMetrics]);

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

  return { orders, totalCount, pageCount, metrics, isLoading, error, isConnected: sse.isConnected, reload };
}
