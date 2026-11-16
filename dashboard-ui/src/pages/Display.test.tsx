import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import Display from './Display';
import type { OrderRow } from '@/types';

let orders: OrderRow[] = [];
let onOrderConnected: ((id: string) => void) | undefined;

vi.mock('@/hooks/useDashboardOrders', () => ({
  useDashboardOrders: (_q: unknown, options?: { onOrderConnected?: (id: string) => void }) => {
    onOrderConnected = options?.onOrderConnected;
    return { orders, totalCount: orders.length, pageCount: 1, metrics: null, isLoading: false, error: null, isConnected: true, reload: vi.fn() };
  },
}));
vi.mock('@/utils/api', async (orig) => ({
  ...(await orig<typeof import('@/utils/api')>()),
  apiClient: { getUserProfile: () => ({ role: 'STAFF' }), getRestaurantProfile: vi.fn().mockResolvedValue({ name: 'Cafe Roma', identifier: 'roma' }) },
}));
vi.mock('@/utils/receipt', () => ({
  trackingUrl: (id: string) => `https://shop.example/track/${id}`,
  qrDataUrl: vi.fn().mockResolvedValue('data:image/png;base64,AAAA'),
}));

const row = (id: string, extra: Partial<OrderRow> = {}): OrderRow => ({
  id, orderId: `#${id}`, customerName: 'Secret Name', itemsOrdered: 'Secret item', deliveryETA: null,
  status: 'CREATED', estimatedWaitTime: null, itemCount: 1, sessionId: `s-${id}`, connected: false, ...extra,
});

describe('Customer display', () => {
  beforeEach(() => { orders = []; });
  afterEach(() => vi.useRealTimers());

  it('waits while there is nothing to hand over', async () => {
    render(<Display />);
    expect(await screen.findByText(/Your order will appear here/)).toBeInTheDocument();
  });

  it('shows the QR code of the newest unconnected order, and nothing personal', async () => {
    orders = [row('N2', { connected: true }), row('N1')];
    render(<Display />);
    expect(await screen.findByAltText('QR code for order #N1')).toBeInTheDocument();
    expect(screen.queryByText(/Secret/)).not.toBeInTheDocument();
  });

  it('replaces the code with a confirmation when the customer connects', async () => {
    orders = [row('N1')];
    render(<Display />);
    await screen.findByAltText('QR code for order #N1');

    vi.useFakeTimers();
    act(() => onOrderConnected?.('N1'));
    expect(screen.getByText('You are connected')).toBeInTheDocument();
    expect(screen.queryByAltText('QR code for order #N1')).not.toBeInTheDocument();

    act(() => { vi.advanceTimersByTime(5000); });
    expect(screen.queryByText('You are connected')).not.toBeInTheDocument();
    expect(screen.queryByAltText('QR code for order #N1')).not.toBeInTheDocument();
  });
});
