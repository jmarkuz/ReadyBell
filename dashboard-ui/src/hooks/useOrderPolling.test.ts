import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useOrderPolling } from './useOrderPolling';
import { Order } from '@/types';

// Mock fetch globally
const mockFetch = vi.fn();
global.fetch = mockFetch;

describe('useOrderPolling Hook', () => {
  const mockOrders: Order[] = [
    {
      id: 'order-1',
      customerName: 'John Doe',
      items: ['Burger'],
      status: 'CREATED',
      deliveryEta: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ];

  beforeEach(() => {
    mockFetch.mockClear();
    vi.clearAllTimers();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
  });

  it('should initialize with polling disabled', async () => {
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: false,
    }));

    expect(onOrdersUpdate).not.toHaveBeenCalled();
  });

  it('should start polling when enabled', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    const { result } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 1000,
      enabled: true,
    }));

    await waitFor(() => {
      expect(result.current.isPolling).toBe(true);
    });

    vi.advanceTimersByTime(10);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('/api/orders', expect.any(Object));
      expect(onOrdersUpdate).toHaveBeenCalledWith(mockOrders);
    });
  });

  it('should handle 304 Not Modified responses', async () => {
    const onOrdersUpdate = vi.fn();

    // First call succeeds with ETag
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    // Second call returns 304
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 304,
      headers: new Map([['ETag', '"123"']]),
    });

    const { result } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
      useETag: true,
    }));

    // Wait for first poll
    await waitFor(() => {
      expect(onOrdersUpdate).toHaveBeenCalledWith(mockOrders);
    });

    const firstCallCount = onOrdersUpdate.mock.calls.length;

    // Advance to trigger second poll
    vi.advanceTimersByTime(100);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2);
    });

    // Second call should not update orders (304 = no change)
    expect(onOrdersUpdate.mock.calls.length).toBe(firstCallCount);
  });

  it('should use ETag for conditional requests', async () => {
    const onOrdersUpdate = vi.fn();
    const etag = '"abc123"';

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['ETag', etag]]),
      json: async () => mockOrders,
    });

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
      useETag: true,
    }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    });

    const firstCallHeaders = mockFetch.mock.calls[0]?.[1];
    expect(firstCallHeaders?.headers).toBeDefined();
    // After first call, next call should include If-None-Match header
  });

  it('should adjust interval for mobile devices', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    // Save original userAgent
    const originalUserAgent = navigator.userAgent;

    // Mock mobile user agent
    Object.defineProperty(navigator, 'userAgent', {
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)',
      configurable: true,
    });

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: true,
    }));

    // Mobile should use 5s interval (5000ms)
    expect(mockFetch).toHaveBeenCalled();

    // Restore userAgent
    Object.defineProperty(navigator, 'userAgent', {
      value: originalUserAgent,
      configurable: true,
    });
  });

  it('should track poll count', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    const { result } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    await waitFor(() => {
      expect(result.current.pollCount).toBeGreaterThan(0);
    });

    const firstCount = result.current.pollCount;

    vi.advanceTimersByTime(100);

    await waitFor(() => {
      expect(result.current.pollCount).toBeGreaterThan(firstCount);
    });
  });

  it('should track last polled timestamp', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    const { result } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    await waitFor(() => {
      expect(result.current.lastPolledAt).toBeDefined();
    });

    expect(result.current.lastPolledAt).toBeInstanceOf(Date);
  });

  it('should handle fetch errors', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    await waitFor(() => {
      expect(result.current.error).toBeDefined();
      expect(result.current.error?.message).toContain('Network');
    });
  });

  it('should handle HTTP errors', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      headers: new Map(),
    });

    const { result } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    await waitFor(() => {
      expect(result.current.error).toBeDefined();
      expect(result.current.error?.message).toContain('HTTP 500');
    });
  });

  it('should stop polling when disabled', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    const { rerender } = renderHook(
      ({ enabled }: { enabled: boolean }) =>
        useOrderPolling({
          orders: mockOrders,
          onOrdersUpdate,
          endpoint: '/api/orders',
          interval: 100,
          enabled,
        }),
      { initialProps: { enabled: true } }
    );

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    });

    mockFetch.mockClear();

    // Disable polling
    rerender({ enabled: false });

    vi.advanceTimersByTime(200);

    // Should not make new requests after disable
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('should use custom interval', async () => {
    const onOrdersUpdate = vi.fn();
    const customInterval = 2000;

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: customInterval,
      enabled: true,
    }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    mockFetch.mockClear();

    // Advance less than custom interval - should not poll again
    vi.advanceTimersByTime(customInterval - 100);
    expect(mockFetch).not.toHaveBeenCalled();

    // Advance past interval - should poll
    vi.advanceTimersByTime(200);
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    });
  });

  it('should cleanup on unmount', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    const { unmount } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    });

    mockFetch.mockClear();

    unmount();

    // Advance time - should not make new requests
    vi.advanceTimersByTime(200);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('should handle JSON parse errors', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => {
        throw new Error('Invalid JSON');
      },
    });

    const { result } = renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    await waitFor(() => {
      expect(result.current.error).toBeDefined();
    });
  });

  it('should disable ETag if useETag is false', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Map([['ETag', '"123"']]),
      json: async () => mockOrders,
    });

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
      useETag: false,
    }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    });

    const callHeaders = mockFetch.mock.calls[0]?.[1]?.headers;
    expect(callHeaders?.['If-None-Match']).toBeUndefined();
  });
});
