import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import IncomingOrdersTable from './IncomingOrdersTable';
import { OrderRow, OrderStatus } from '@/types';

const row = (id: string, status: OrderStatus): OrderRow => ({
  id, orderId: `#${id}`, customerName: 'Anna', itemsOrdered: 'Soup', deliveryETA: null,
  status, estimatedWaitTime: null, itemCount: 1,
});

describe('IncomingOrdersTable status menu', () => {
  it('offers only the allowed next statuses and reports the chosen one', async () => {
    const onStatusChange = vi.fn();
    const user = userEvent.setup();
    render(<IncomingOrdersTable orders={[row('A1', 'PREPARING')]} onStatusChange={onStatusChange} />);

    await user.click(screen.getByRole('button', { name: /update status for order #A1/i }));
    const menu = screen.getByRole('menu');
    const items = within(menu).getAllByRole('menuitem').map((i) => i.textContent);
    expect(items).toEqual(['Mark ready for pickup', 'Cancel order']);

    await user.click(within(menu).getByRole('menuitem', { name: 'Mark ready for pickup' }));
    expect(onStatusChange).toHaveBeenCalledWith('A1', 'READY');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('disables the button for finished orders', () => {
    render(<IncomingOrdersTable orders={[row('A1', 'COMPLETED'), row('B2', 'CANCELLED')]} />);
    expect(screen.getByRole('button', { name: /update status for order #A1/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /update status for order #B2/i })).toBeDisabled();
  });

  it('closes on Escape and when clicking elsewhere', async () => {
    const user = userEvent.setup();
    render(<IncomingOrdersTable orders={[row('A1', 'CREATED')]} />);
    const open = screen.getByRole('button', { name: /update status for order #A1/i });

    await user.click(open);
    expect(screen.getByRole('menu')).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();

    await user.click(open);
    await user.click(document.body);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('opens one menu at a time', async () => {
    const user = userEvent.setup();
    render(<IncomingOrdersTable orders={[row('A1', 'CREATED'), row('B2', 'READY')]} />);
    await user.click(screen.getByRole('button', { name: /update status for order #A1/i }));
    await user.click(screen.getByRole('button', { name: /update status for order #B2/i }));
    expect(screen.getAllByRole('menu')).toHaveLength(1);
    expect(screen.getByRole('menuitem', { name: 'Mark completed' })).toBeInTheDocument();
  });
});
