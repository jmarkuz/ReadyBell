import { useCallback, useEffect, useRef, useState } from 'react';
import { OrderMetrics, OrderRow, OrderStatus } from '@/types';
import { apiClient } from '@/utils/api';
import { useSSE } from './useSSE';

const POLL_INTERVAL_MS = 30_000;

const RELOAD_DEBOUNCE_MS = 300;

/** A new order announced by the live stream (`order-created`). */
export interface NewOrderEvent {
  id: string;
  orderNumber?: string;
}

function asNewOrderEvent(data: unknown): NewOrderEvent | null {
  if (typeof data !== 'object' || data === null) return null;
  const message = data as { event?: unknown; id?: unknown; orderNumber?: unknown };
  if (message.event !== 'order-created' || typeof message.id !== 'string') return null;
  return { id: message.id, orderNumber: typeof message.orderNumber === 'string' ? message.orderNumber : undefined };
}

/** The customer opened an order's tracking link (the QR code was scanned): staff stream only. */
function asConnectedId(data: unknown): string | null {
  if (typeof data !== 'object' || data === null) return null;
  const message = data as { event?: unknown; id?: unknown };
  return message.event === 'order-connected' && typeof message.id === 'string' ? message.id : null;
}

export type OrdersSortBy = 'created' | 'eta' | 'status' | 'customerName' | 'orderId';

/** Everything the server needs to produce one page of the Incoming Orders table. */
export interface OrdersQuery {
  page: number;
  pageSize: number;
  statuses: OrderStatus[];
  search: string;
  from: Date | null;
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
  orders: OrderRow[];
  totalCount: number;
  pageCount: number;
  /** Real per-status totals for the restaurant, independent of filters and paging (null until loaded). */
  metrics: OrderMetrics | null;
  /** True only until the first page has loaded; later queries keep showing the previous rows. */
  isLoading: boolean;
  error: Error | null;
  isConnected: boolean;
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

/** Loads one page of the tenant's orders from the backend and keeps it fresh. */
export function useDashboardOrders(
  query: OrdersQuery,
  options: { onNewOrder?: (order: NewOrderEvent) => void; onOrderConnected?: (orderId: string) => void } = {}
): UseDashboardOrdersState {
  const [orders, setOrders] = useState<OrderRow[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [pageCount, setPageCount] = useState(0);
  const [metrics, setMetrics] = useState<OrderMetrics | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const mounted = useRef(true);

  const onNewOrderRef = useRef(options.onNewOrder);
  const onOrderConnectedRef = useRef(options.onOrderConnected);
  useEffect(() => {
    onNewOrderRef.current = options.onNewOrder;
    onOrderConnectedRef.current = options.onOrderConnected;
  });

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
  const onMessage = useCallback((data: unknown) => {
    const created = asNewOrderEvent(data);
    if (created) onNewOrderRef.current?.(created);
    const connectedId = asConnectedId(data);
    if (connectedId) onOrderConnectedRef.current?.(connectedId);
    if (debounce.current) clearTimeout(debounce.current);
    debounce.current = setTimeout(() => void reload(), RELOAD_DEBOUNCE_MS);
  }, [reload]);

  useEffect(() => () => {
    if (debounce.current) clearTimeout(debounce.current);
  }, []);

  const sse = useSSE({
    endpoint: '/api/orders/stream',
    getTicket: () => apiClient.createStreamTicket(),
    onMessage,
  });

  useEffect(() => {
    if (sse.isConnected) return undefined;
    const timer = setInterval(() => void reload(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [sse.isConnected, reload]);

  return { orders, totalCount, pageCount, metrics, isLoading, error, isConnected: sse.isConnected, reload };
}
