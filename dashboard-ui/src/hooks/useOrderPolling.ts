import { useCallback, useEffect, useRef, useState } from 'react';
import { Order } from '@/types';

export interface UseOrderPollingOptions {
  orders: Order[];
  onOrdersUpdate: (orders: Order[]) => void;
  endpoint?: string;
  enabled?: boolean;
  interval?: number;
  useETag?: boolean;
}

export interface UseOrderPollingState {
  isPolling: boolean;
  error: Error | null;
  lastPolledAt: Date | null;
  pollCount: number;
}

/**
 * Hook for polling order updates as a fallback when SSE is unavailable.
 * Uses ETag for conditional requests to minimize bandwidth.
 *
 * Features:
 * - Configurable polling interval
 * - ETag support for conditional requests (304 Not Modified)
 * - Automatic interval adjustment based on device type
 * - Error handling and recovery
 *
 * @param options - Configuration options
 * @returns Polling state and status
 */
export function useOrderPolling(options: UseOrderPollingOptions): UseOrderPollingState {
  const {
    onOrdersUpdate,
    endpoint = '/api/orders',
    enabled = true,
    interval: customInterval,
    useETag = true,
  } = options;

  const [state, setState] = useState<UseOrderPollingState>({
    isPolling: false,
    error: null,
    lastPolledAt: null,
    pollCount: 0,
  });

  const etagRef = useRef<string | null>(null);
  const pollTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const isUnmountingRef = useRef(false);

  // Determine default interval based on device
  const getDefaultInterval = useCallback((): number => {
    // Check if mobile device
    const isMobile =
      typeof window !== 'undefined' &&
      /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(
        navigator.userAgent
      );
    // Mobile: 5s, Desktop/Tablet: 10s
    return isMobile ? 5000 : 10000;
  }, []);

  const pollInterval = customInterval ?? getDefaultInterval();

  const poll = useCallback(async () => {
    if (isUnmountingRef.current || !enabled) return;

    try {
      setState((prev) => ({
        ...prev,
        isPolling: true,
      }));

      const headers: Record<string, string> = {};
      if (useETag && etagRef.current) {
        headers['If-None-Match'] = etagRef.current;
      }

      const response = await fetch(endpoint, { headers });

      // 304 Not Modified - data hasn't changed
      if (response.status === 304) {
        setState((prev) => ({
          ...prev,
          isPolling: false,
          lastPolledAt: new Date(),
          pollCount: prev.pollCount + 1,
        }));

        // Schedule next poll
        if (!isUnmountingRef.current) {
          pollTimeoutRef.current = setTimeout(poll, pollInterval);
        }
        return;
      }

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      // Get ETag from response
      if (useETag) {
        const etag = response.headers.get('ETag');
        if (etag) {
          etagRef.current = etag;
        }
      }

      const data = (await response.json()) as Order[];

      if (!isUnmountingRef.current && Array.isArray(data)) {
        onOrdersUpdate(data);

        setState((prev) => ({
          ...prev,
          isPolling: false,
          error: null,
          lastPolledAt: new Date(),
          pollCount: prev.pollCount + 1,
        }));
      }
    } catch (error) {
      const err = error instanceof Error ? error : new Error('Polling failed');

      setState((prev) => ({
        ...prev,
        isPolling: false,
        error: err,
      }));

      console.error('Order polling error:', err);
    }

    // Schedule next poll
    if (!isUnmountingRef.current && enabled) {
      pollTimeoutRef.current = setTimeout(poll, pollInterval);
    }
  }, [endpoint, enabled, onOrdersUpdate, pollInterval, useETag]);

  useEffect(() => {
    isUnmountingRef.current = false;

    if (enabled) {
      // Start polling immediately
      poll();
    }

    return () => {
      isUnmountingRef.current = true;

      // Clear any pending poll
      if (pollTimeoutRef.current) {
        clearTimeout(pollTimeoutRef.current);
        pollTimeoutRef.current = null;
      }
    };
  }, [enabled, poll]);

  return state;
}
