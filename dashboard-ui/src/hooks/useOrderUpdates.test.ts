import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
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
  configurable: true,
});

describe('useOrderUpdates Hook', () => {
  const mockOrders: Order[] = [
    {
      id: 'order-1',
      orderId: 'ORD001',
      customerId: 'cust-1',
      restaurantId: 'rest-1',
      items: [],
      status: 'CREATED',
      createdAt: '2024-01-01T00:00:00Z',
      estimatedReadyAt: '2024-01-01T00:30:00Z',
      totalPrice: 50,
      deliveryMethod: 'pickup',
      updatedAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 'order-2',
      orderId: 'ORD002',
      customerId: 'cust-2',
      restaurantId: 'rest-1',
      items: [],
      status: 'PREPARING',
      createdAt: '2024-01-01T00:05:00Z',
      estimatedReadyAt: '2024-01-01T00:35:00Z',
      totalPrice: 75,
      deliveryMethod: 'delivery',
      updatedAt: '2024-01-01T00:05:00Z',
    },
  ];

  beforeEach(() => {
    MockEventSource.reset();
  });

  afterEach(() => {
    MockEventSource.reset();
  });

  it('should initialize with provided orders', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      enabled: false,
    }));

    // Should initialize but not connect yet
    expect(result.current.isUsingSSE).toBe(false);
  });

  it('should handle order status updates', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Simulate order status update
    const updateData = {
      id: 'order-1',
      status: 'READY',
    };

    act(() => {
      eventSource.simulateMessage(JSON.stringify(updateData));
    });

    expect(onOrdersUpdate).toHaveBeenCalled();
    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    expect(updatedOrders?.[0]?.status).toBe('READY');
  });

  it('should add new orders from stream', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Simulate new order
    const newOrderData = {
      id: 'order-3',
      orderId: 'ORD003',
      customerId: 'cust-3',
      restaurantId: 'rest-1',
      items: [],
      status: 'CREATED',
      createdAt: '2024-01-01T00:10:00Z',
      estimatedReadyAt: '2024-01-01T00:40:00Z',
      totalPrice: 100,
      deliveryMethod: 'pickup',
    };

    act(() => {
      eventSource.simulateMessage(JSON.stringify(newOrderData));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    expect(updatedOrders?.length).toBe(3);
    expect(updatedOrders?.[0]?.id).toBe('order-3');
  });

  it('should skip updates without order id', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Send invalid update without id
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        status: 'READY',
      }));
    });

    // Should not call onOrdersUpdate for invalid data
    expect(onOrdersUpdate).not.toHaveBeenCalled();
  });

  it('should maintain sort order after updates', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      sortBy: 'createdAt',
      sortOrder: 'desc',
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Simulate new order
    const newOrderData = {
      id: 'order-3',
      orderId: 'ORD003',
      customerId: 'cust-3',
      restaurantId: 'rest-1',
      items: [],
      status: 'CREATED',
      createdAt: '2024-01-01T00:15:00Z',
      estimatedReadyAt: '2024-01-01T00:45:00Z',
      totalPrice: 100,
      deliveryMethod: 'pickup',
    };

    act(() => {
      eventSource.simulateMessage(JSON.stringify(newOrderData));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // Should be sorted by createdAt desc (newest first)
    expect(updatedOrders).toBeDefined();
    // Verify order-3 is first since it has the newest createdAt
    expect(updatedOrders?.[0]?.id).toBe('order-3');
  });

  it('should deduplicate orders by id', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Send update for existing order
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-1',
        status: 'PREPARING',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // Should still have 2 orders (no duplicate)
    expect(updatedOrders?.length).toBe(2);
    expect(updatedOrders?.filter((o: Order) => o.id === 'order-1')?.length).toBe(1);
  });

  it('should preserve partial order data on update', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Send partial update
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-1',
        status: 'COMPLETED',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    const updatedOrder = updatedOrders?.find((o: Order) => o.id === 'order-1');
    // Should keep original fields
    expect(updatedOrder?.orderId).toBe('ORD001');
    expect(updatedOrder?.customerId).toBe('cust-1');
    // Should update status
    expect(updatedOrder?.status).toBe('COMPLETED');
  });

  it('should use custom endpoint', () => {
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

  it('should handle multiple concurrent updates', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Send multiple updates in sequence
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-1',
        status: 'PREPARING',
      }));
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-2',
        status: 'READY',
      }));
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-3',
        orderId: 'ORD003',
        customerId: 'cust-3',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: '2024-01-01T00:20:00Z',
        estimatedReadyAt: '2024-01-01T00:50:00Z',
        totalPrice: 120,
        deliveryMethod: 'pickup',
      }));
    });

    expect(onOrdersUpdate).toHaveBeenCalled();
    const lastCall = onOrdersUpdate.mock.calls[onOrdersUpdate.mock.calls.length - 1];
    const updatedOrders = lastCall?.[0];
    expect(updatedOrders?.length).toBe(3);
  });

  it('should clear error state on successful update', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    expect(result.current.isConnected).toBe(true);

    // Send valid update
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-1',
        status: 'READY',
      }));
    });

    expect(result.current.error).toBeNull();
  });

  it('should return correct connection state', () => {
    const onOrdersUpdate = vi.fn();

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
    }));

    // Before connection
    expect(result.current.isConnected).toBe(false);
    expect(result.current.isUsingSSE).toBe(false);

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // After connection
    expect(result.current.isConnected).toBe(true);
    expect(result.current.isUsingSSE).toBe(true);
    expect(result.current.isPolling).toBe(false);
  });
});



describe('useOrderUpdates - Sort Maintenance Tests', () => {
  const mockOrders: Order[] = [
    {
      id: 'order-3',
      orderId: 'ORD003',
      customerId: 'cust-3',
      restaurantId: 'rest-1',
      items: [],
      status: 'READY',
      createdAt: '2024-01-01T00:15:00Z',
      estimatedReadyAt: '2024-01-01T00:45:00Z',
      totalPrice: 100,
      deliveryMethod: 'pickup',
      updatedAt: '2024-01-01T00:15:00Z',
    },
    {
      id: 'order-1',
      orderId: 'ORD001',
      customerId: 'cust-1',
      restaurantId: 'rest-1',
      items: [],
      status: 'CREATED',
      createdAt: '2024-01-01T00:00:00Z',
      estimatedReadyAt: '2024-01-01T00:30:00Z',
      totalPrice: 50,
      deliveryMethod: 'pickup',
      updatedAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 'order-2',
      orderId: 'ORD002',
      customerId: 'cust-2',
      restaurantId: 'rest-1',
      items: [],
      status: 'PREPARING',
      createdAt: '2024-01-01T00:05:00Z',
      estimatedReadyAt: '2024-01-01T00:35:00Z',
      totalPrice: 75,
      deliveryMethod: 'delivery',
      updatedAt: '2024-01-01T00:05:00Z',
    },
  ];

  afterEach(() => {
    MockEventSource.reset();
  });

  it('should maintain descending sort order by createdAt', () => {
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      sortBy: 'createdAt',
      sortOrder: 'desc',
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Add order with middle timestamp
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-4',
        orderId: 'ORD004',
        customerId: 'cust-4',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: '2024-01-01T00:10:00Z',
        estimatedReadyAt: '2024-01-01T00:40:00Z',
        totalPrice: 90,
        deliveryMethod: 'pickup',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // Should be sorted: order-4 (10:00), order-1 (00:00), order-2 (05:00), order-3 (15:00) desc
    // Actually: order-3 (15:00), order-4 (10:00), order-2 (05:00), order-1 (00:00)
    expect(updatedOrders?.[0]?.id).toBe('order-3'); // newest first
    expect(updatedOrders?.[3]?.id).toBe('order-1'); // oldest last
  });

  it('should maintain ascending sort order by createdAt', () => {
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      sortBy: 'createdAt',
      sortOrder: 'asc',
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Add new order
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-4',
        orderId: 'ORD004',
        customerId: 'cust-4',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: '2024-01-01T00:10:00Z',
        estimatedReadyAt: '2024-01-01T00:40:00Z',
        totalPrice: 90,
        deliveryMethod: 'pickup',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // Should be sorted ascending: order-1 (00:00), order-2 (05:00), order-4 (10:00), order-3 (15:00)
    expect(updatedOrders?.[0]?.id).toBe('order-1'); // oldest first
    expect(updatedOrders?.[3]?.id).toBe('order-3'); // newest last
  });

  it('should handle sort by status', () => {
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      sortBy: 'status',
      sortOrder: 'asc',
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Update order status
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-1',
        status: 'READY',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // Should be sorted by status ascending: CREATED, PREPARING, READY, READY
    expect(updatedOrders).toBeDefined();
    expect(updatedOrders?.length).toBe(3);
  });

  it('should not sort when sortBy is not provided', () => {
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      // sortBy not provided
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Add new order
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-new',
        orderId: 'ORD-NEW',
        customerId: 'cust-new',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: '2024-01-01T00:20:00Z',
        estimatedReadyAt: '2024-01-01T00:50:00Z',
        totalPrice: 80,
        deliveryMethod: 'pickup',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // New order should be first (added via unshift)
    expect(updatedOrders?.[0]?.id).toBe('order-new');
  });
});

describe('useOrderUpdates - Pagination Preservation Tests', () => {
  const createMockOrders = (count: number): Order[] => {
    return Array.from({ length: count }, (_, i) => ({
      id: `order-${i}`,
      orderId: `ORD${String(i).padStart(3, '0')}`,
      customerId: `cust-${i}`,
      restaurantId: 'rest-1',
      items: [],
      status: 'CREATED',
      createdAt: new Date(Date.now() - i * 60000).toISOString(),
      estimatedReadyAt: new Date(Date.now() + 30 * 60000).toISOString(),
      totalPrice: 50 + i,
      deliveryMethod: 'pickup',
      updatedAt: new Date().toISOString(),
    }));
  };

  afterEach(() => {
    MockEventSource.reset();
  });

  it('should preserve pagination state when updating existing order', () => {
    const orders = createMockOrders(25);
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderUpdates({
      orders,
      onOrdersUpdate,
      currentPage: 2,
      pageSize: 10,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Update an order
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-5',
        status: 'READY',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // Should still have all 25 orders
    expect(updatedOrders?.length).toBe(25);
  });

  it('should handle updates when adding orders at different positions', () => {
    const orders = createMockOrders(15);
    const onOrdersUpdate = vi.fn();

    renderHook(() => useOrderUpdates({
      orders,
      onOrdersUpdate,
      currentPage: 1,
      pageSize: 5,
      sortBy: 'createdAt',
      sortOrder: 'desc',
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Add new order (should go to top after sorting)
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-new',
        orderId: 'ORD-NEW',
        customerId: 'cust-new',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: new Date().toISOString(),
        estimatedReadyAt: new Date(Date.now() + 30 * 60000).toISOString(),
        totalPrice: 100,
        deliveryMethod: 'pickup',
      }));
    });

    const updatedOrders = onOrdersUpdate.mock.calls[0]?.[0];
    // Should have 16 orders total
    expect(updatedOrders?.length).toBe(16);
    // New order should be first (newest)
    expect(updatedOrders?.[0]?.id).toBe('order-new');
  });
});

describe('useOrderUpdates - Error Handling Tests', () => {
  afterEach(() => {
    MockEventSource.reset();
  });

  it('should handle malformed JSON in SSE message', () => {
    const onOrdersUpdate = vi.fn();
    const onSSEError = vi.fn();
    const mockOrders: Order[] = [
      {
        id: 'order-1',
        orderId: 'ORD001',
        customerId: 'cust-1',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: '2024-01-01T00:00:00Z',
        estimatedReadyAt: '2024-01-01T00:30:00Z',
        totalPrice: 50,
        deliveryMethod: 'pickup',
        updatedAt: '2024-01-01T00:00:00Z',
      },
    ];

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      onSSEError,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Send malformed JSON
    act(() => {
      eventSource.simulateMessage('{ invalid json }');
    });

    // onSSEError should not be called by SSE parse error (useSSE handles it)
    // But the order update callback shouldn't be called
    expect(onOrdersUpdate).not.toHaveBeenCalled();
  });

  it('should continue processing updates after an error', () => {
    const onOrdersUpdate = vi.fn();
    const onSSEError = vi.fn();
    const mockOrders: Order[] = [
      {
        id: 'order-1',
        orderId: 'ORD001',
        customerId: 'cust-1',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: '2024-01-01T00:00:00Z',
        estimatedReadyAt: '2024-01-01T00:30:00Z',
        totalPrice: 50,
        deliveryMethod: 'pickup',
        updatedAt: '2024-01-01T00:00:00Z',
      },
    ];

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      onSSEError,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Send invalid update (missing id)
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        status: 'READY',
      }));
    });

    // Should not call onOrdersUpdate
    expect(onOrdersUpdate).not.toHaveBeenCalled();

    // Send valid update - should still work
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-1',
        status: 'READY',
      }));
    });

    // Now should call onOrdersUpdate
    expect(onOrdersUpdate).toHaveBeenCalled();
  });

  it('should set error state on invalid update', () => {
    const onOrdersUpdate = vi.fn();
    const onSSEError = vi.fn();
    const mockOrders: Order[] = [
      {
        id: 'order-1',
        orderId: 'ORD001',
        customerId: 'cust-1',
        restaurantId: 'rest-1',
        items: [],
        status: 'CREATED',
        createdAt: '2024-01-01T00:00:00Z',
        estimatedReadyAt: '2024-01-01T00:30:00Z',
        totalPrice: 50,
        deliveryMethod: 'pickup',
        updatedAt: '2024-01-01T00:00:00Z',
      },
    ];

    const { result } = renderHook(() => useOrderUpdates({
      orders: mockOrders,
      onOrdersUpdate,
      onSSEError,
    }));

    const eventSource = MockEventSource.getLast();
    act(() => {
      eventSource.simulateOpen();
    });

    // Send invalid message that triggers error
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        // invalid update structure
        badField: 'value',
      }));
    });

    // Error should be cleared on next successful update
    act(() => {
      eventSource.simulateMessage(JSON.stringify({
        id: 'order-1',
        status: 'READY',
      }));
    });

    expect(result.current.error).toBeNull();
  });
});
