import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useOrderUpdates } from './useOrderUpdates';
import { Order } from '@/types';

// Mock EventSource for this test file
class MockEventSource {
  static instances: MockEventSource[] = [];

  url: string;
  listeners: Record<string, ((event: Event) => void)[]> = {
    open: [],
    message: [],
    error: [],
  };

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(event: string, callback: (event: Event) => void) {
    if (event in this.listeners) {
      this.listeners[event].push(callback);
    }
  }

  removeEventListener(event: string, callback: (event: Event) => void) {
    if (event in this.listeners) {
      this.listeners[event] = this.listeners[event].filter(l => l !== callback);
    }
  }

  close() {
    // Mock close
  }

  simulateOpen() {
    this.listeners.open.forEach(cb => cb(new Event('open')));
  }

  simulateMessage(data: string) {
    const event = new MessageEvent('message', { data });
    this.listeners.message.forEach(cb => cb(event));
  }

  simulateError() {
    this.listeners.error.forEach(cb => cb(new Event('error')));
  }

  static reset() {
    MockEventSource.instances = [];
  }

  static getLast() {
    return MockEventSource.instances[MockEventSource.instances.length - 1];
  }
}

Object.defineProperty(global, 'EventSource', {
  value: MockEventSource,
  writable: true,
});

describe('useOrderUpdates Hook', () => {
  const mockOrders: Order[] = [
    {
      id: 'order-1',
      customerName: 'John Doe',
      items: ['Burger', 'Fries'],
      status: 'CREATED',
      deliveryEta: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      id: 'order-2',
      customerName: 'Jane Smith',
      items: ['Pizza'],
      status: 'PREPARING',
      deliveryEta: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ];

  beforeEach(() => {
    MockEventSource.reset();
    vi.clearAllTimers();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    MockEventSource.reset();
  });

  it('should initialize with provided orders', () => {
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: false, // Don't connect in this test
    }));

    expect(onOrdersUpdate).not.toHaveBeenCalled();
  });

  it('should handle order status updates', async () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    eventSource.simulateOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Simulate order update
    const updateData = {
      id: 'order-1',
      status: 'READY',
    };

    eventSource.simulateMessage(JSON.stringify(updateData));

    await waitFor(() => {
      expect(onOrdersUpdate).toHaveBeenCalled();
      const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
      expect(updatedOrders?.[0]?.status).toBe('READY');
    });
  });

  it('should add new orders from stream', async () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    eventSource.simulateOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Simulate new order
    const newOrderData = {
      id: 'order-3',
      customerName: 'Bob Johnson',
      items: ['Sandwich'],
      status: 'CREATED',
      deliveryEta: new Date().toISOString(),
    };

    eventSource.simulateMessage(JSON.stringify(newOrderData));

    await waitFor(() => {
      const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
      expect(updatedOrders?.length).toBeGreaterThan(mockOrders.length);
      expect(updatedOrders?.[0]?.id).toBe('order-3');
    });
  });

  it('should track last update timestamp', async () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    eventSource.simulateOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    expect(result.current.lastUpdate).toBeNull();

    eventSource.simulateMessage(JSON.stringify({
      id: 'order-1',
      status: 'READY',
    }));

    await waitFor(() => {
      expect(result.current.lastUpdate).toBeDefined();
      expect(result.current.lastUpdate).toBeInstanceOf(Date);
    });
  });

  it('should handle connection errors', async () => {
    const onOrdersUpdate = vi.fn();
    const onError = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: true,
    }));

    const eventSource = MockEventSource.getLast();
    eventSource.simulateError();

    await waitFor(() => {
      expect(result.current.isRetrying).toBe(true);
      expect(result.current.error).toBeDefined();
    });
  });

  it('should skip updates without order id', async () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    eventSource.simulateOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Send invalid update without id
    eventSource.simulateMessage(JSON.stringify({
      status: 'READY',
      // Missing id
    }));

    // Should not trigger update with invalid data
    const initialCalls = onOrdersUpdate.mock.calls.length;
    eventSource.simulateMessage(JSON.stringify({
      status: 'READY',
    }));

    expect(onOrdersUpdate.mock.calls.length).toBe(initialCalls);
  });

  it('should maintain order references in updates', async () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    eventSource.simulateOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Update first order
    eventSource.simulateMessage(JSON.stringify({
      id: 'order-1',
      status: 'READY',
    }));

    await waitFor(() => {
      const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
      // Second order should remain unchanged
      expect(updatedOrders?.[1]?.id).toBe('order-2');
      expect(updatedOrders?.[1]?.status).toBe('PREPARING');
    });
  });

  it('should allow disabling updates', async () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: false,
    }));

    // Should have no EventSource in disabled mode (or it shouldn't process messages)
    // The hook should not set isConnected to true
    expect(result.current.isConnected).toBe(false);
  });

  it('should handle partial order updates', async () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    eventSource.simulateOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Send partial update (only status changed)
    eventSource.simulateMessage(JSON.stringify({
      id: 'order-1',
      status: 'COMPLETED',
    }));

    await waitFor(() => {
      const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
      const updatedOrder = updatedOrders?.find((o: Order) => o.id === 'order-1');
      // Should keep original fields
      expect(updatedOrder?.customerName).toBe('John Doe');
      expect(updatedOrder?.items).toEqual(['Burger', 'Fries']);
      // Should update new fields
      expect(updatedOrder?.status).toBe('COMPLETED');
    });
  });

  it('should use custom endpoint', async () => {
    const onOrdersUpdate = vi.fn();
    const customEndpoint = '/api/custom/orders/stream';

    renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      endpoint: customEndpoint,
    }));

    const eventSource = MockEventSource.getLast();
    expect(eventSource.url).toBe(customEndpoint);
  });

  it('should cleanup on unmount', () => {
    const onOrdersUpdate = vi.fn();

    const { unmount } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    const closeSpy = vi.spyOn(eventSource, 'close');

    unmount();

    expect(closeSpy).toHaveBeenCalled();
  });
});
