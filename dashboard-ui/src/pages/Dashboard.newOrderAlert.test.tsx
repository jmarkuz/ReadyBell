import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, act, fireEvent } from '@/test/test-utils';
import Dashboard from './Dashboard';
import type { NewOrderEvent } from '@/hooks/useDashboardOrders';

const notifyNewOrder = vi.fn();
let onNewOrder: ((order: NewOrderEvent) => void) | undefined;
const reload = vi.fn().mockResolvedValue(undefined);

vi.mock('@/context/alertContext', () => ({
  useNewOrderAlerts: () => ({ soundEnabled: true, setSoundEnabled: vi.fn(), notifyNewOrder }),
}));
vi.mock('@/hooks/useDashboardOrders', () => ({
  useDashboardOrders: (_query: unknown, options?: { onNewOrder?: (order: NewOrderEvent) => void }) => {
    onNewOrder = options?.onNewOrder;
    return { orders: [], totalCount: 0, pageCount: 0, metrics: null, isLoading: false, error: null, isConnected: true, reload };
  },
}));
// Only the "order created" callback matters here
vi.mock('@/components/NewOrderDialog', () => ({
  default: ({ onCreated }: { onCreated: (orderId: string) => void }) => (
    <button type="button" onClick={() => onCreated('own-1')}>create own order</button>
  ),
}));

describe('Dashboard new-order alerts', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    notifyNewOrder.mockClear();
    reload.mockClear();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('alerts staff to an order entered somewhere else, with its number', () => {
    render(<Dashboard />);

    act(() => onNewOrder?.({ id: 'remote-1', orderNumber: '#12' }));
    act(() => { vi.advanceTimersByTime(3000); });

    expect(notifyNewOrder).toHaveBeenCalledTimes(1);
    expect(notifyNewOrder).toHaveBeenCalledWith('#12');
  });

  it('stays quiet for an order created on this very screen, even when the event beats the response', () => {
    render(<Dashboard />);

    act(() => onNewOrder?.({ id: 'own-1', orderNumber: '#13' }));   // the server event arrives first
    fireEvent.click(screen.getByRole('button', { name: 'create own order' }));   // then our POST answers
    act(() => { vi.advanceTimersByTime(3000); });

    expect(notifyNewOrder).not.toHaveBeenCalled();
    expect(reload).toHaveBeenCalled();
  });

  it('does not alert after the page is closed', () => {
    const { unmount } = render(<Dashboard />);
    act(() => onNewOrder?.({ id: 'remote-2', orderNumber: '#14' }));
    unmount();
    act(() => { vi.advanceTimersByTime(3000); });

    expect(notifyNewOrder).not.toHaveBeenCalled();
  });
});
