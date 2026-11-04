import { useEffect, useRef, useState, useCallback } from 'react';

export interface UseSSEOptions {
  endpoint: string;
  onMessage: (data: unknown) => void;
  onError?: (error: Error) => void;
  maxRetries?: number;
  initialDelay?: number;
  /**
   * Returns a single-use stream ticket. Called before every (re)connect and sent as `?ticket=`,
   * because EventSource cannot set an Authorization header and the JWT must not be put in a URL.
   */
  getTicket?: () => Promise<string>;
  onClose?: () => void;
}

export interface UseSSEState {
  isConnected: boolean;
  isRetrying: boolean;
  error: Error | null;
}

/**
 * Custom hook for Server-Sent Events (SSE) with automatic reconnection
 * and exponential backoff retry strategy.
 *
 * Features:
 * - Auto-reconnect with exponential backoff (1s, 2s, 4s, 8s, max 30s)
 * - Handles connection loss and restoration
 * - Typed message callbacks
 * - Single-use ticket authentication (fresh ticket per connection)
 * - Error handling and reporting
 * - Maximum 1 concurrent SSE connection per hook instance
 *
 * @param options - Configuration options
 * @returns Connection state and status
 *
 * @example
 * ```tsx
 * const state = useSSE({
 *   endpoint: '/api/orders/stream',
 *   getTicket: () => apiClient.createStreamTicket(),
 *   onMessage: (data) => console.log('Order update:', data),
 *   onError: (error) => console.error('SSE error:', error),
 * });
 * ```
 */
export function useSSE(options: UseSSEOptions): UseSSEState {
  const {
    endpoint,
    onMessage,
    onError,
    getTicket,
    onClose,
    maxRetries = Infinity,
    initialDelay = 1000,
  } = options;

  const [state, setState] = useState<UseSSEState>({
    isConnected: false,
    isRetrying: false,
    error: null,
  });

  const eventSourceRef = useRef<EventSource | null>(null);
  const retryCountRef = useRef(0);
  const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const isUnmountingRef = useRef(false);

  const calculateBackoffDelay = useCallback((): number => {
    const exponentialDelay = initialDelay * Math.pow(2, retryCountRef.current);
    // Cap at 30 seconds
    return Math.min(exponentialDelay, 30000);
  }, [initialDelay]);

  /** Schedules a reconnect with exponential backoff, or gives up after maxRetries. */
  const scheduleRetry = useCallback(() => {
    if (retryCountRef.current < maxRetries) {
      const delay = calculateBackoffDelay();
      retryCountRef.current += 1;
      retryTimeoutRef.current = setTimeout(() => {
        void connectRef.current();
      }, delay);
    } else {
      setState((prev) => ({
        ...prev,
        error: new Error('Max retries exceeded'),
        isRetrying: false,
      }));
      onError?.(new Error('Max retries exceeded'));
      onClose?.();
    }
  }, [maxRetries, calculateBackoffDelay, onError, onClose]);

  const connectRef = useRef<() => Promise<void>>(async () => {});

  const connect = useCallback(async () => {
    if (isUnmountingRef.current) return;

    try {
      let connectionUrl = endpoint;
      if (getTicket) {
        let ticket: string;
        try {
          ticket = await getTicket();
        } catch {
          // No ticket (offline, session expired): same backoff as a dropped connection
          if (!isUnmountingRef.current) {
            setState({ isConnected: false, isRetrying: true, error: new Error('Could not get a stream ticket') });
            scheduleRetry();
          }
          return;
        }
        if (isUnmountingRef.current) return;
        const separator = endpoint.includes('?') ? '&' : '?';
        connectionUrl = `${endpoint}${separator}ticket=${encodeURIComponent(ticket)}`;
      }

      const eventSource = new EventSource(connectionUrl);

      eventSource.addEventListener('open', () => {
        if (!isUnmountingRef.current) {
          setState({
            isConnected: true,
            isRetrying: false,
            error: null,
          });
          retryCountRef.current = 0;
        }
      });

      const handleMessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data);
          onMessage(data);
        } catch (parseError) {
          const error = parseError instanceof Error ? parseError : new Error('Failed to parse SSE message');
          setState((prev) => ({
            ...prev,
            error,
          }));
          onError?.(error);
        }
      };

      // Unnamed events, and the backend's named `order-status-updated` event
      eventSource.addEventListener('message', handleMessage);
      eventSource.addEventListener('order-status-updated', handleMessage as EventListener);

      eventSource.addEventListener('error', () => {
        if (!isUnmountingRef.current) {
          eventSource.close();
          setState({
            isConnected: false,
            isRetrying: true,
            error: new Error('SSE connection failed'),
          });

          scheduleRetry();
        }
      });

      eventSourceRef.current = eventSource;
    } catch (error) {
      const err = error instanceof Error ? error : new Error('Failed to create EventSource');
      setState({
        isConnected: false,
        isRetrying: false,
        error: err,
      });
      onError?.(err);
      onClose?.();
    }
  }, [endpoint, onMessage, onError, onClose, getTicket, scheduleRetry]);
  connectRef.current = connect;

  useEffect(() => {
    isUnmountingRef.current = false;
    void connect();

    return () => {
      isUnmountingRef.current = true;

      // Clean up EventSource
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }

      // Clear any pending retry
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
        retryTimeoutRef.current = null;
      }

      onClose?.();
    };
  }, [connect, onClose]);

  return state;
}
