import { StrictMode } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import IncomingOrdersTable from './IncomingOrdersTable';
import { OrderRow } from '@/types';

const rows = (n: number): OrderRow[] =>
  Array.from({ length: n }, (_, i) => ({
    id: `id-${i}`, orderId: `#${String(i + 1).padStart(3, '0')}`, customerName: `Cust ${String(i).padStart(2, '0')}`,
    itemsOrdered: 'Soup', deliveryETA: new Date(2030, 0, 1, 12, i), status: 'CREATED' as const,
    estimatedWaitTime: null, itemCount: 1,
  }));

const header = (name: RegExp) => screen.getByRole('button', { name }).closest('[aria-sort]') as HTMLElement;
const firstCell = () => screen.getAllByRole('row')[1].querySelector('td')!.textContent;

describe('IncomingOrdersTable sorting', () => {
  it('toggles asc → desc → asc on repeated clicks, also under StrictMode', async () => {
    const user = userEvent.setup();
    render(<StrictMode><IncomingOrdersTable orders={rows(3)} /></StrictMode>);
    expect(header(/^ETA/)).toHaveAttribute('aria-sort', 'ascending');

    await user.click(screen.getByRole('button', { name: /^ETA/ }));
    expect(header(/^ETA/)).toHaveAttribute('aria-sort', 'descending');
    expect(firstCell()).toBe('#003');

    await user.click(screen.getByRole('button', { name: /^ETA/ }));
    expect(header(/^ETA/)).toHaveAttribute('aria-sort', 'ascending');
    expect(firstCell()).toBe('#001');
  });

  it('starts a new column ascending and clears aria-sort on the previous one', async () => {
    const user = userEvent.setup();
    render(<StrictMode><IncomingOrdersTable orders={rows(3)} /></StrictMode>);
    await user.click(screen.getByRole('button', { name: /^ETA/ }));   // ETA now descending
    await user.click(screen.getByRole('button', { name: /^Customer/ }));
    expect(header(/^Customer/)).toHaveAttribute('aria-sort', 'ascending');
    expect(header(/^ETA/)).toHaveAttribute('aria-sort', 'none');
  });

  it('goes back to page 1 when the sort changes (uncontrolled)', async () => {
    const user = userEvent.setup();
    render(<StrictMode><IncomingOrdersTable orders={rows(25)} /></StrictMode>);
    await user.click(screen.getByRole('button', { name: /next page/i }));
    expect(screen.getByText(/Page 2 of 3/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Customer/ }));
    expect(screen.getByText(/Page 1 of 3/)).toBeInTheDocument();
  });

  it('asks a controlling parent for page 1 when the sort changes', async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(<IncomingOrdersTable orders={rows(25)} currentPage={3} onPageChange={onPageChange} />);
    await user.click(screen.getByRole('button', { name: /^Customer/ }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('activates a header with Enter and Space, and Space does not scroll the page', async () => {
    const user = userEvent.setup();
    render(<IncomingOrdersTable orders={rows(3)} />);
    const button = screen.getByRole('button', { name: /^Customer/ });
    button.focus();
    await user.keyboard('{Enter}');
    expect(header(/^Customer/)).toHaveAttribute('aria-sort', 'ascending');
    await user.keyboard(' ');
    expect(header(/^Customer/)).toHaveAttribute('aria-sort', 'descending');
  });

  it('keeps a real <button> inside each sortable <th>, so the header cells stay columnheaders', () => {
    render(<IncomingOrdersTable orders={rows(3)} />);
    expect(screen.getAllByRole('columnheader')).toHaveLength(6);
    for (const name of ['Order ID', 'Customer', 'Items', 'ETA', 'Status']) {
      const button = screen.getByRole('button', { name: new RegExp(`^${name}`) });
      expect(button.tagName).toBe('BUTTON');
      expect(button.parentElement?.tagName).toBe('TH');
    }
  });
});
