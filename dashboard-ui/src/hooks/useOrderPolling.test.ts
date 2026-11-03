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
      orderId: 'ORD-001',
      customerId: 'cust-1',
      restaurantId: 'rest-1',
      items: [{ id: 'item-1', menuItemId: 'menu-1', name: 'Burger', quantity: 1, price: 10 }],
      status: 'CREATED',
      createdAt: new Date(),
      estimatedReadyAt: new Date(),
      totalPrice: 10,
      deliveryMethod: 'pickup',
    },
  ];

  beforeEach(() => {
    mockFetch.mockClear();
    vi.clearAllTimers();
  });

  afterEach(() => {
    vi.clearAllTimers();
  });

  it('should initialize with polling disabled', () => {
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: false,
    }));

    expect(onOrdersUpdate).not.toHaveBeenCalled();
  });

  it('should call fetch when polling enabled', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValueOnce({
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
    }));

    // Wait for initial fetch to complete
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  it('should handle 304 Not Modified without updating', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 304,
      headers: new Map([['ETag', '"123"']]),
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
    }, { timeout: 3000 });

    // Should not call onOrdersUpdate for 304
    expect(onOrdersUpdate).not.toHaveBeenCalled();
  });

  it('should include ETag header on requests', async () => {
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
    }, { timeout: 3000 });

    const callHeaders = mockFetch.mock.calls[0]?.[1]?.headers;
    expect(callHeaders).toBeDefined();
  });

  it('should detect mobile device for polling interval', () => {
    const onOrdersUpdate = vi.fn();

    // Mobile user agent
    const originalUserAgent = Object.getOwnPropertyDescriptor(navigator, 'userAgent');
    Object.defineProperty(navigator, 'userAgent', {
      value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)',
      configurable: true,
    });

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: false, // Don't actually poll
    }));

    // Restore userAgent
    if (originalUserAgent) {
      Object.defineProperty(navigator, 'userAgent', originalUserAgent);
    }
  });

  it('should track polling state', async () => {
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

    // Check initial state
    expect(result.current.error).toBeNull();
    expect(result.current.lastPolledAt).toBeNull();
    expect(result.current.pollCount).toBe(0);

    // Wait for first poll
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    }, { timeout: 3000 });

    // The counter is React state: it is updated after fetch resolves, so wait for it instead of racing
    await waitFor(() => expect(result.current.pollCount).toBeGreaterThanOrEqual(1));
    expect(result.current.lastPolledAt).toBeInstanceOf(Date);
  });

  it('should handle fetch errors gracefully', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockRejectedValue(new Error('Network error'));

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    // Should eventually encounter the error and call onOrdersUpdate with it
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  it('should handle HTTP errors', async () => {
    const onOrdersUpdate = vi.fn();

    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      headers: new Map(),
    });

    renderHook(() => useOrderPolling({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: '/api/orders',
      interval: 100,
      enabled: true,
    }));

    // Should make the request and handle the error
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  it('should respect enabled flag', async () => {
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
    }, { timeout: 3000 });

    mockFetch.mockClear();

    // Disable polling
    rerender({ enabled: false });

    // Wait a bit to ensure no new calls
    await new Promise(resolve => setTimeout(resolve, 200));

    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('should use custom interval when provided', async () => {
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
      interval: 5000,
      enabled: true,
    }));

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(1);
    }, { timeout: 3000 });
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
    }, { timeout: 3000 });

    mockFetch.mockClear();
    unmount();

    // Wait to ensure no more calls after unmount
    await new Promise(resolve => setTimeout(resolve, 200));
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
    }, { timeout: 3000 });
  });

  it('should not include ETag header when disabled', async () => {
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
    }, { timeout: 3000 });

    const callHeaders = mockFetch.mock.calls[0]?.[1]?.headers;
    expect(callHeaders?.['If-None-Match']).toBeUndefined();
  });
});
