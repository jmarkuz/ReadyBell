import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import IncomingOrdersTable from './IncomingOrdersTable';
import { OrderRow } from '@/types';

const row = (id: string, extra: Partial<OrderRow> = {}): OrderRow => ({
  id, orderId: `#${id}`, customerName: 'Anna', itemsOrdered: 'Soup', deliveryETA: null,
  status: 'CREATED', estimatedWaitTime: null, itemCount: 1, sessionId: `s-${id}`, ...extra,
});

describe('IncomingOrdersTable QR code and connection', () => {
  it('asks to show the QR code of the clicked order', async () => {
    const onRowClick = vi.fn();
    render(<IncomingOrdersTable orders={[row('A1'), row('B2')]} onRowClick={onRowClick} />);
    await userEvent.click(screen.getByRole('button', { name: 'Show QR code for order #B2' }));
    expect(onRowClick).toHaveBeenCalledWith('B2');
  });

  it('marks orders whose customer connected, from the server or from the live stream', () => {
    render(<IncomingOrdersTable
      orders={[row('A1', { connected: true }), row('B2'), row('C3')]}
      connectedIds={new Set(['C3'])} />);
    expect(screen.getAllByText('Connected')).toHaveLength(2);
  });
});
