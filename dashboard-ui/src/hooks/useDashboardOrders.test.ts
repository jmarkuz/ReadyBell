import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useDashboardOrders } from './useDashboardOrders';
import { apiClient } from '@/utils/api';
import * as sse from './useSSE';

vi.mock('@/utils/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/utils/api')>();
  return { ...original, apiClient: { getOrders: vi.fn() } };
});

const row = (id: string) => ({
  id, orderId: `#${id}`, customerName: 'Anna', itemsOrdered: 'Burger', deliveryETA: null,
  status: 'CREATED' as const, estimatedWaitTime: null, itemCount: 1,
});
const page = (...ids: string[]) => ({ items: ids.map(row), totalCount: ids.length, pageCount: 1, currentPage: 1 });

describe('useDashboardOrders', () => {
  let onMessage: (data: unknown) => void;
  let connected = false;

  beforeEach(() => {
    vi.mocked(apiClient.getOrders).mockReset().mockResolvedValue(page('A'));
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

  it('loads the newest orders from the API on mount', async () => {
    const { result } = renderHook(() => useDashboardOrders());
    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.orders.map((o) => o.id)).toEqual(['A']);
    expect(apiClient.getOrders).toHaveBeenCalledWith({ page: 1, pageSize: 100, sortBy: 'created', sortOrder: 'desc' });
  });

  it('reports a load error and keeps going', async () => {
    vi.mocked(apiClient.getOrders).mockRejectedValueOnce(new Error('boom'));
    const { result } = renderHook(() => useDashboardOrders());
    await waitFor(() => expect(result.current.error?.message).toBe('boom'));
    expect(result.current.isLoading).toBe(false);
    expect(result.current.orders).toEqual([]);

    vi.mocked(apiClient.getOrders).mockResolvedValue(page('B'));
    await act(async () => { await result.current.reload(); });
    expect(result.current.error).toBeNull();
    expect(result.current.orders.map((o) => o.id)).toEqual(['B']);
  });

  it('coalesces a burst of SSE events into one reload', async () => {
    const { result } = renderHook(() => useDashboardOrders());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    vi.mocked(apiClient.getOrders).mockClear().mockResolvedValue(page('A', 'C'));

    vi.useFakeTimers();
    act(() => { onMessage({ id: 'A' }); onMessage({ id: 'C' }); onMessage({ id: 'A' }); });
    await act(async () => { await vi.advanceTimersByTimeAsync(400); });

    expect(apiClient.getOrders).toHaveBeenCalledTimes(1);
    expect(result.current.orders.map((o) => o.id)).toEqual(['A', 'C']);
  });

  /** Mounts under fake timers (so the interval uses the fake clock) and lets the initial load settle. */
  async function mountWithFakeTimers() {
    vi.useFakeTimers();
    const hook = renderHook(() => useDashboardOrders());
    await act(async () => { await vi.advanceTimersByTimeAsync(0); });
    expect(hook.result.current.isLoading).toBe(false);
    vi.mocked(apiClient.getOrders).mockClear();
    return hook;
  }

  it('polls every 30 s while the stream is down', async () => {
    await mountWithFakeTimers();

    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(apiClient.getOrders).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(apiClient.getOrders).toHaveBeenCalledTimes(2);
  });

  it('does not poll while the stream is connected', async () => {
    connected = true;
    await mountWithFakeTimers();

    await act(async () => { await vi.advanceTimersByTimeAsync(90_000); });
    expect(apiClient.getOrders).not.toHaveBeenCalled();
  });
});
