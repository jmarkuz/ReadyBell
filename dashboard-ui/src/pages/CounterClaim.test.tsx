import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import CounterClaim from './CounterClaim';
import { parseOrderNumber } from '@/utils/orderNumber';
import * as trackApi from '@/utils/trackApi';

vi.mock('@/utils/trackApi', async (orig) => ({
  ...(await orig<typeof trackApi>()),
  fetchCounterRestaurant: vi.fn(),
  claimOrder: vi.fn(),
}));

function Tracking() {
  return <p>tracking {useLocation().pathname}</p>;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/r/cafe']}>
      <Routes>
        <Route path="/r/:identifier" element={<CounterClaim />} />
        <Route path="/track/:sessionId" element={<Tracking />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(trackApi.fetchCounterRestaurant).mockResolvedValue('Café Aurora');
});

describe('parseOrderNumber', () => {
  it('accepts digits with an optional leading #', () => {
    expect(parseOrderNumber('101')).toBe(101);
    expect(parseOrderNumber(' #101 ')).toBe(101);
    expect(parseOrderNumber('# 7')).toBe(7);
  });
  it('rejects everything else', () => {
    for (const bad of ['', 'abc', '0', '-3', '1.5', '12345678', '#', '10 1']) expect(parseOrderNumber(bad)).toBeNull();
  });
});

describe('CounterClaim page', () => {
  it('shows the restaurant and sends the customer to the tracking page of the claimed order', async () => {
    vi.mocked(trackApi.claimOrder).mockResolvedValue('session-9');
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Café Aurora')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Order number'), '#101');
    await user.click(screen.getByRole('button', { name: 'Show my order' }));

    expect(trackApi.claimOrder).toHaveBeenCalledWith('cafe', 101);
    expect(await screen.findByText('tracking /track/session-9')).toBeInTheDocument();
  });

  it('rejects a malformed number without calling the server', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Order number'), 'abc');
    await user.click(screen.getByRole('button', { name: 'Show my order' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter the number from your receipt');
    expect(trackApi.claimOrder).not.toHaveBeenCalled();
  });

  it('shows one generic message when no order is found', async () => {
    vi.mocked(trackApi.claimOrder).mockRejectedValue(new trackApi.OrderNotFoundError());
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Order number'), '999');
    await user.click(screen.getByRole('button', { name: 'Show my order' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('could not find an order with this number');
    expect(screen.getByRole('button', { name: 'Show my order' })).toBeEnabled();
  });

  it('asks the customer to wait after too many attempts', async () => {
    vi.mocked(trackApi.claimOrder).mockRejectedValue(new trackApi.TooManyAttemptsError());
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Order number'), '5');
    await user.click(screen.getByRole('button', { name: 'Show my order' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts');
  });

  it('explains an invalid QR code when the restaurant is unknown', async () => {
    vi.mocked(trackApi.fetchCounterRestaurant).mockResolvedValue(null);
    renderPage();

    expect(await screen.findByText('This QR code is not valid')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByLabelText('Order number')).not.toBeInTheDocument());
  });
});
