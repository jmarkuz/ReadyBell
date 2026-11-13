import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useSSE } from './useSSE';

describe('useSSE Hook', () => {
  let mockEventSource: any;
  let originalEventSource: typeof EventSource;

  beforeEach(() => {
    originalEventSource = (global as any).EventSource;
    
    // Create a mock EventSource factory
    mockEventSource = {
      listeners: {} as Record<string, Function[]>,
      addEventListener(event: string, callback: Function) {
        if (!this.listeners[event]) {
          this.listeners[event] = [];
        }
        this.listeners[event].push(callback);
      },
      close: vi.fn(),
      dispatchOpen() {
        this.listeners['open']?.forEach((cb: Function) => cb(new Event('open')));
      },
      dispatchMessage(data: string) {
        const event = new MessageEvent('message', { data });
        this.listeners['message']?.forEach((cb: Function) => cb(event));
      },
      /** A named server event, as the backend sends `order-created` and `order-status-updated`. */
      dispatchNamed(name: string, data: string) {
        const event = new MessageEvent(name, { data });
        this.listeners[name]?.forEach((cb: Function) => cb(event));
      },
      dispatchError() {
        this.listeners['error']?.forEach((cb: Function) => cb(new Event('error')));
      },
    };

    (global as any).EventSource = vi.fn(() => mockEventSource);
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
    (global as any).EventSource = originalEventSource;
  });

  it('should create EventSource with provided endpoint', () => {
    const onMessage = vi.fn();
    const endpoint = '/api/orders/stream';

    renderHook(() => useSSE({ endpoint, onMessage }));

    expect((global as any).EventSource).toHaveBeenCalledWith(endpoint);
  });

  it('should append a single-use ticket to the URL when getTicket is provided', async () => {
    const onMessage = vi.fn();
    const endpoint = '/api/orders/stream';
    const authToken = 'test-ticket-12345';

    renderHook(() => useSSE({ endpoint, onMessage, getTicket: async () => authToken }));
    await waitFor(() => expect((global as any).EventSource).toHaveBeenCalled());

    const callUrl = (global as any).EventSource.mock.calls[0][0];
    expect(callUrl).toContain(endpoint);
    expect(callUrl).toContain(`ticket=${encodeURIComponent(authToken)}`);
    expect(callUrl).not.toContain('token=');
  });

  it('should handle ticket encoding correctly', async () => {
    const onMessage = vi.fn();
    const endpoint = '/api/orders/stream';
    const authToken = 'ticket-with-special-chars!@#$%';

    renderHook(() => useSSE({ endpoint, onMessage, getTicket: async () => authToken }));
    await waitFor(() => expect((global as any).EventSource).toHaveBeenCalled());

    const callUrl = (global as any).EventSource.mock.calls[0][0];
    expect(callUrl).toContain(encodeURIComponent(authToken));
  });

  it('should handle endpoint with existing query parameters', async () => {
    const onMessage = vi.fn();
    const endpoint = '/api/orders/stream?filter=active';

    renderHook(() => useSSE({ endpoint, onMessage, getTicket: async () => 'test-ticket' }));
    await waitFor(() => expect((global as any).EventSource).toHaveBeenCalled());

    const callUrl = (global as any).EventSource.mock.calls[0][0];
    expect(callUrl).toContain('?filter=active');
    expect(callUrl).toContain('&ticket=');
  });

  it('should call onMessage when message event is received', async () => {
    const onMessage = vi.fn();
    const messageData = { id: 'order-1', status: 'PREPARING' };

    const { result } = renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage 
    }));

    expect(result.current.isConnected).toBe(false);

    // Simulate connection open
    mockEventSource.dispatchOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Send a message
    mockEventSource.dispatchMessage(JSON.stringify(messageData));

    await waitFor(() => {
      expect(onMessage).toHaveBeenCalledWith(messageData);
    });
  });

  it('delivers the backend\'s named order-created and order-status-updated events', async () => {
    const onMessage = vi.fn();
    renderHook(() => useSSE({ endpoint: '/api/orders/stream', onMessage }));
    mockEventSource.dispatchOpen();

    const created = { id: 'o-1', status: 'CREATED', event: 'order-created', orderNumber: '#5' };
    const updated = { id: 'o-1', status: 'READY' };
    mockEventSource.dispatchNamed('order-created', JSON.stringify(created));
    mockEventSource.dispatchNamed('order-status-updated', JSON.stringify(updated));

    await waitFor(() => expect(onMessage).toHaveBeenCalledTimes(2));
    expect(onMessage).toHaveBeenNthCalledWith(1, created);
    expect(onMessage).toHaveBeenNthCalledWith(2, updated);
  });

  it('should set isRetrying when error occurs', async () => {
    const onMessage = vi.fn();

    const { result } = renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage,
    }));

    mockEventSource.dispatchError();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(false);
      expect(result.current.isRetrying).toBe(true);
      expect(result.current.error).toBeDefined();
    });
  });

  it('should call onError when provided', async () => {
    const onMessage = vi.fn();
    const onError = vi.fn();

    renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage,
      onError,
      maxRetries: 0,
    }));

    mockEventSource.dispatchError();

    await waitFor(() => {
      expect(onError).toHaveBeenCalled();
    });
  });

  it('should handle invalid JSON gracefully', async () => {
    const onMessage = vi.fn();
    const onError = vi.fn();

    const { result } = renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage,
      onError,
    }));

    mockEventSource.dispatchOpen();
    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Send invalid JSON
    mockEventSource.dispatchMessage('not valid json');

    await waitFor(() => {
      expect(result.current.error).toBeDefined();
      expect(result.current.error?.message).toMatch(/parse|Unexpected/i);
      expect(onError).toHaveBeenCalled();
    });
  });

  it('should close EventSource on unmount', () => {
    const onMessage = vi.fn();

    const { unmount } = renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage,
    }));

    unmount();

    expect(mockEventSource.close).toHaveBeenCalled();
  });

  it('should respect maxRetries option', async () => {
    const onMessage = vi.fn();
    const onError = vi.fn();

    const { result } = renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage,
      onError,
      maxRetries: 0,
    }));

    mockEventSource.dispatchError();

    await waitFor(() => {
      expect(result.current.error?.message).toBe('Max retries exceeded');
    });
  });

  it('should reset error when successfully reconnected', async () => {
    const onMessage = vi.fn();

    const { result } = renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage,
    }));

    // Simulate initial error
    mockEventSource.dispatchError();
    
    await waitFor(() => {
      expect(result.current.error).toBeDefined();
    });

    // Simulate recovery (open event)
    mockEventSource.dispatchOpen();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
      expect(result.current.error).toBeNull();
      expect(result.current.isRetrying).toBe(false);
    });
  });

  it('should handle multiple messages correctly', async () => {
    const onMessage = vi.fn();
    const messages = [
      { id: 'order-1', status: 'PREPARING' },
      { id: 'order-2', status: 'READY' },
    ];

    renderHook(() => useSSE({ 
      endpoint: '/api/orders/stream', 
      onMessage,
    }));

    mockEventSource.dispatchOpen();

    for (const message of messages) {
      mockEventSource.dispatchMessage(JSON.stringify(message));
    }

    await waitFor(() => {
      expect(onMessage).toHaveBeenCalledTimes(2);
      expect(onMessage).toHaveBeenNthCalledWith(1, messages[0]);
      expect(onMessage).toHaveBeenNthCalledWith(2, messages[1]);
    });
  });

  it('should call onClose when connection is closed on unmount', () => {
    const onMessage = vi.fn();
    const onClose = vi.fn();

    const { unmount } = renderHook(() => useSSE({
      endpoint: '/api/orders/stream',
      onMessage,
      onClose,
    }));

    unmount();

    expect(onClose).toHaveBeenCalled();
  });

  it('should call onClose when max retries are exceeded', async () => {
    const onMessage = vi.fn();
    const onClose = vi.fn();
    const onError = vi.fn();

    renderHook(() => useSSE({
      endpoint: '/api/orders/stream',
      onMessage,
      onError,
      onClose,
      maxRetries: 0,
    }));

    mockEventSource.dispatchError();

    await waitFor(() => {
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('should support exponential backoff retry strategy', async () => {
    const onMessage = vi.fn();
    const onError = vi.fn();

    const { result } = renderHook(() => useSSE({
      endpoint: '/api/orders/stream',
      onMessage,
      onError,
      initialDelay: 100,
      maxRetries: 2,
    }));

    expect(result.current.isRetrying).toBe(false);

    // Trigger error
    mockEventSource.dispatchError();
    
    await waitFor(() => {
      expect(result.current.isRetrying).toBe(true);
    });

    // Verify error is set
    expect(result.current.error).toBeDefined();
  });

  it('should maintain connection state across multiple messages', async () => {
    const onMessage = vi.fn();

    const { result } = renderHook(() => useSSE({
      endpoint: '/api/orders/stream',
      onMessage,
    }));

    mockEventSource.dispatchOpen();
    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Send messages
    mockEventSource.dispatchMessage(JSON.stringify({ orderId: 'order-1' }));
    mockEventSource.dispatchMessage(JSON.stringify({ orderId: 'order-2' }));

    await waitFor(() => {
      expect(onMessage).toHaveBeenCalledTimes(2);
    });

    // Verify connection is still active
    expect(result.current.isConnected).toBe(true);
  });

  it('should request a fresh ticket for every (re)connect', async () => {
    const getTicket = vi.fn()
      .mockResolvedValueOnce('ticket-1')
      .mockResolvedValue('ticket-2');

    renderHook(() => useSSE({ endpoint: '/api/orders/stream', onMessage: vi.fn(), getTicket, initialDelay: 10 }));
    await waitFor(() => expect((global as any).EventSource).toHaveBeenCalledTimes(1));
    expect((global as any).EventSource.mock.calls[0][0]).toContain('ticket=ticket-1');

    // Drop the connection: the retry must not reuse the consumed ticket
    act(() => { mockEventSource.dispatchError(); });
    await waitFor(() => expect((global as any).EventSource).toHaveBeenCalledTimes(2));
    expect((global as any).EventSource.mock.calls[1][0]).toContain('ticket=ticket-2');
  });

  it('should keep one connection when getTicket is a new function on every render', async () => {
    const onMessage = vi.fn();     // stable, like the callback in useDashboardOrders
    const { rerender } = renderHook(
      () => useSSE({ endpoint: '/api/orders/stream', onMessage, getTicket: async () => 'ticket' }));
    await waitFor(() => expect((global as any).EventSource).toHaveBeenCalledTimes(1));

    rerender();
    rerender();
    await new Promise((r) => setTimeout(r, 50));

    expect((global as any).EventSource).toHaveBeenCalledTimes(1);
    expect(mockEventSource.close).not.toHaveBeenCalled();
  });

  it('should retry when the ticket request fails', async () => {
    const getTicket = vi.fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue('ticket-ok');

    const { result } = renderHook(() =>
      useSSE({ endpoint: '/api/orders/stream', onMessage: vi.fn(), getTicket, initialDelay: 10 }));
    await waitFor(() => expect(result.current.isRetrying).toBe(true));
    await waitFor(() => expect((global as any).EventSource).toHaveBeenCalledTimes(1));
    expect((global as any).EventSource.mock.calls[0][0]).toContain('ticket=ticket-ok');
  });

  it('should handle connection loss and set error state', async () => {
    const onMessage = vi.fn();
    const onError = vi.fn();

    const { result } = renderHook(() => useSSE({
      endpoint: '/api/orders/stream',
      onMessage,
      onError,
      maxRetries: 0,
    }));

    mockEventSource.dispatchOpen();
    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    // Simulate connection loss
    mockEventSource.dispatchError();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(false);
      expect(result.current.error).toBeDefined();
      expect(onError).toHaveBeenCalled();
    });
  });
});
