import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useDashboardOrders, DEFAULT_ORDERS_QUERY, type OrdersQuery } from './useDashboardOrders';
import { apiClient } from '@/utils/api';
import * as sse from './useSSE';

vi.mock('@/utils/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/utils/api')>();
  return { ...original, apiClient: { getOrders: vi.fn(), getOrderMetrics: vi.fn() } };
});

const row = (id: string) => ({
  id, orderId: `#${id}`, customerName: 'Anna', itemsOrdered: 'Burger', deliveryETA: null,
  status: 'CREATED' as const, estimatedWaitTime: null, itemCount: 1,
});
const page = (...ids: string[]) => ({ items: ids.map(row), totalCount: ids.length, pageCount: 1, currentPage: 1 });

/** Requests for the table page (metrics have their own endpoint). */
const calls = () => vi.mocked(apiClient.getOrders).mock.calls.map(([params]) => params ?? {});
const pageCalls = () => calls();

describe('useDashboardOrders', () => {
  let onMessage: (data: unknown) => void;
  let connected = false;

  beforeEach(() => {
    vi.mocked(apiClient.getOrders).mockReset().mockResolvedValue(page('A'));
    vi.mocked(apiClient.getOrderMetrics).mockReset().mockResolvedValue({
      totalOrders: 300, newOrders: 1, preparing: 2, readyForPickup: 3, completed: 290, cancelled: 4,
    });
    connected = false;
    vi.spyOn(sse, 'useSSE').mockImplementation((options) => {
      onMessage = options.onMessage;
      return { isConnected: connected, isRetrying: false, error: null };
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('loads the requested page from the API on mount', async () => {
    const { result } = renderHook(() => useDashboardOrders(DEFAULT_ORDERS_QUERY));
    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.orders.map((o) => o.id)).toEqual(['A']);
    expect(pageCalls()).toEqual([{ page: 1, pageSize: 10, sortBy: 'created', sortOrder: 'desc' }]);
  });

  it('passes filters, search, dates and sorting to the server', async () => {
    const query: OrdersQuery = {
      page: 3, pageSize: 10, statuses: ['PREPARING', 'READY'], search: '  burger ',
      from: new Date('2030-01-01T00:00:00Z'), to: new Date('2030-01-31T23:59:59Z'),
      sortBy: 'customerName', sortOrder: 'asc',
    };
    const { result } = renderHook(() => useDashboardOrders(query));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(pageCalls()).toEqual([{
      page: 3, pageSize: 10, sortBy: 'customerName', sortOrder: 'asc',
      status: ['PREPARING', 'READY'], search: 'burger',
      from: '2030-01-01T00:00:00.000Z', to: '2030-01-31T23:59:59.000Z',
    }]);
  });

  it('exposes the total and page counts of the whole result', async () => {
    vi.mocked(apiClient.getOrders).mockResolvedValue({ items: [row('A')], totalCount: 250, pageCount: 25, currentPage: 1 });
    const { result } = renderHook(() => useDashboardOrders(DEFAULT_ORDERS_QUERY));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.totalCount).toBe(250);
    expect(result.current.pageCount).toBe(25);
  });

  it('loads real totals from the metrics endpoint, independent of the page filters', async () => {
    const { result } = renderHook(() => useDashboardOrders({ ...DEFAULT_ORDERS_QUERY, statuses: ['READY'] }));
    await waitFor(() => expect(result.current.metrics?.totalOrders).toBe(300));
    expect(apiClient.getOrderMetrics).toHaveBeenCalledTimes(1);
  });

  it('keeps the last metrics and the orders when the metrics request fails', async () => {
    const { result } = renderHook(() => useDashboardOrders(DEFAULT_ORDERS_QUERY));
    await waitFor(() => expect(result.current.metrics?.totalOrders).toBe(300));
    vi.mocked(apiClient.getOrderMetrics).mockRejectedValue(new Error('boom'));
    await act(async () => { await result.current.reload(); });
    expect(result.current.metrics?.totalOrders).toBe(300);
    expect(result.current.error).toBeNull();
  });

  it('reports a load error and keeps going', async () => {
    vi.mocked(apiClient.getOrders).mockRejectedValueOnce(new Error('boom'));
    const { result } = renderHook(() => useDashboardOrders(DEFAULT_ORDERS_QUERY));
    await waitFor(() => expect(result.current.error?.message).toBe('boom'));
    expect(result.current.isLoading).toBe(false);
    expect(result.current.orders).toEqual([]);

    vi.mocked(apiClient.getOrders).mockResolvedValue(page('B'));
    await act(async () => { await result.current.reload(); });
    expect(result.current.error).toBeNull();
    expect(result.current.orders.map((o) => o.id)).toEqual(['B']);
  });

  describe('when the query changes', () => {
    it('sends exactly one page request, keeps the old rows meanwhile and does not reload the metrics', async () => {
      const { result, rerender } = renderHook((q: OrdersQuery) => useDashboardOrders(q), { initialProps: DEFAULT_ORDERS_QUERY });
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      vi.mocked(apiClient.getOrders).mockClear();
      vi.mocked(apiClient.getOrderMetrics).mockClear();

      let release: (value: ReturnType<typeof page>) => void = () => {};
      vi.mocked(apiClient.getOrders).mockReturnValue(new Promise((resolve) => { release = resolve; }));
      rerender({ ...DEFAULT_ORDERS_QUERY, search: 'pizza' });

      expect(pageCalls()).toHaveLength(1);
      expect(pageCalls()[0]).toMatchObject({ search: 'pizza', page: 1 });
      expect(apiClient.getOrderMetrics).not.toHaveBeenCalled();
      expect(result.current.isLoading).toBe(false);                       // no skeleton flash
      expect(result.current.orders.map((o) => o.id)).toEqual(['A']);      // previous rows stay

      await act(async () => { release(page('P')); });
      expect(result.current.orders.map((o) => o.id)).toEqual(['P']);
    });

    it('does not request again for an equal query object', async () => {
      const { result, rerender } = renderHook((q: OrdersQuery) => useDashboardOrders(q), { initialProps: DEFAULT_ORDERS_QUERY });
      await waitFor(() => expect(result.current.isLoading).toBe(false));
      vi.mocked(apiClient.getOrders).mockClear();

      rerender({ ...DEFAULT_ORDERS_QUERY, statuses: [], from: null });
      expect(apiClient.getOrders).not.toHaveBeenCalled();
    });

    it('drops a slow response that arrives after a newer request', async () => {
      const resolvers: Array<(value: ReturnType<typeof page>) => void> = [];
      vi.mocked(apiClient.getOrders).mockImplementation(() =>
        new Promise((resolve) => { resolvers.push(resolve); }));

      const { result, rerender } = renderHook((q: OrdersQuery) => useDashboardOrders(q), { initialProps: DEFAULT_ORDERS_QUERY });
      rerender({ ...DEFAULT_ORDERS_QUERY, search: 'new' });
      expect(resolvers).toHaveLength(2);

      await act(async () => { resolvers[1](page('NEW')); });
      await act(async () => { resolvers[0](page('OLD')); });

      expect(result.current.orders.map((o) => o.id)).toEqual(['NEW']);
      expect(result.current.isLoading).toBe(false);
    });
  });

  it('coalesces a burst of SSE events into one reload of the current page', async () => {
    const query = { ...DEFAULT_ORDERS_QUERY, page: 2 };
    const { result } = renderHook(() => useDashboardOrders(query));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    vi.mocked(apiClient.getOrders).mockClear().mockResolvedValue(page('A', 'C'));

    vi.useFakeTimers();
    act(() => { onMessage({ id: 'A' }); onMessage({ id: 'C' }); onMessage({ id: 'A' }); });
    await act(async () => { await vi.advanceTimersByTimeAsync(400); });

    expect(pageCalls()).toHaveLength(1);
    expect(pageCalls()[0]).toMatchObject({ page: 2 });     // stays on page 2, no jump to the first
    expect(result.current.orders.map((o) => o.id)).toEqual(['A', 'C']);
  });

  /** Mounts under fake timers (so the interval uses the fake clock) and lets the initial load settle. */
  async function mountWithFakeTimers() {
    vi.useFakeTimers();
    const hook = renderHook(() => useDashboardOrders(DEFAULT_ORDERS_QUERY));
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    expect(hook.result.current.isLoading).toBe(false);
    vi.mocked(apiClient.getOrders).mockClear();
    return hook;
  }

  it('polls every 30 s while the stream is down', async () => {
    await mountWithFakeTimers();

    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(pageCalls()).toHaveLength(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(pageCalls()).toHaveLength(2);
  });

  it('does not poll while the stream is connected', async () => {
    connected = true;
    await mountWithFakeTimers();

    await act(async () => { await vi.advanceTimersByTimeAsync(90_000); });
    expect(apiClient.getOrders).not.toHaveBeenCalled();
  });
});
