import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import IncomingOrdersTable from './IncomingOrdersTable';
import { OrderRow } from '@/types';

const rows = (n: number): OrderRow[] =>
  Array.from({ length: n }, (_, i) => ({
    id: `id-${i}`, orderId: `#${String(i + 1).padStart(3, '0')}`, customerName: 'Anna', itemsOrdered: 'Soup',
    deliveryETA: new Date(2030, 0, 1, 12, i), status: 'CREATED' as const, estimatedWaitTime: null, itemCount: 1,
  }));

const dataRows = () => screen.getAllByRole('row').length - 1;   // minus the header row

describe('IncomingOrdersTable pagination', () => {
  it('shows no pagination for 10 orders or fewer', () => {
    render(<IncomingOrdersTable orders={rows(10)} />);
    expect(dataRows()).toBe(10);
    expect(screen.queryByRole('region', { name: /table pagination/i })).not.toBeInTheDocument();
  });

  it('shows pagination from the 11th order: 10 on page 1, the last one on page 2', async () => {
    const user = userEvent.setup();
    render(<IncomingOrdersTable orders={rows(11)} />);
    expect(dataRows()).toBe(10);
    expect(screen.getByRole('region', { name: /table pagination/i })).toBeInTheDocument();
    expect(screen.getByText(/Page 1 of 2/)).toBeInTheDocument();
    expect(screen.queryByText('#011')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /next page/i }));
    expect(dataRows()).toBe(1);
    expect(screen.getByText('#011')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /next page/i })).toBeDisabled();
  });

  it('pages through 10 orders at a time when there are more than 10', async () => {
    const user = userEvent.setup();
    render(<IncomingOrdersTable orders={rows(25)} />);
    expect(dataRows()).toBe(10);
    expect(screen.getByText(/Page 1 of 3/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /previous page/i })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /next page/i }));
    expect(screen.getByText(/Page 2 of 3/)).toBeInTheDocument();
    expect(screen.getByText('#011')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /next page/i }));
    expect(dataRows()).toBe(5);
    expect(screen.getByRole('button', { name: /next page/i })).toBeDisabled();
  });

  it('falls back to the last page when the list shrinks', async () => {
    const user = userEvent.setup();
    const { rerender } = render(<IncomingOrdersTable orders={rows(25)} />);
    await user.click(screen.getByRole('button', { name: /next page/i }));
    await user.click(screen.getByRole('button', { name: /next page/i }));

    rerender(<IncomingOrdersTable orders={rows(12)} />);
    expect(screen.getByText(/Page 2 of 2/)).toBeInTheDocument();
    expect(dataRows()).toBe(2);
  });
});
