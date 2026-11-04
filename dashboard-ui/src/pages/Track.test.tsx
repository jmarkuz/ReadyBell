import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Track from './Track';
import * as trackApi from '@/utils/trackApi';
import * as push from '@/utils/push';

vi.mock('@/utils/trackApi', async (orig) => ({ ...(await orig<typeof trackApi>()), fetchTrack: vi.fn() }));
vi.mock('@/utils/push', () => ({
  pushSupport: vi.fn(() => 'supported'),
  registerServiceWorker: vi.fn(),
  subscribeToPush: vi.fn(),
}));

const order = (status: trackApi.TrackedOrder['status']): trackApi.TrackedOrder => ({
  orderNumber: '#ABCD1234', status, estimatedReadyAt: null, estimatedWaitMinutes: 7,
  items: [{ name: 'Latte', quantity: 2 }], updatedAt: '2026-01-01T00:00:00Z',
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/track/s-1']}>
      <Routes><Route path="/track/:sessionId" element={<Track />} /></Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  vi.mocked(push.pushSupport).mockReturnValue('supported');
});

describe('Track page', () => {
  it('shows the restaurant, order number, status, wait time and items', async () => {
    vi.mocked(trackApi.fetchTrack).mockResolvedValue({ restaurantName: 'Café Aurora', sessionActive: true, orders: [order('IN_PROGRESS')] });
    renderPage();
    expect(await screen.findByText('#ABCD1234')).toBeInTheDocument();
    expect(screen.getByText('Café Aurora')).toBeInTheDocument();
    expect(screen.getByText('Being prepared')).toBeInTheDocument();
    expect(screen.getByText('about 7 min')).toBeInTheDocument();
    expect(screen.getByText('2× Latte')).toBeInTheDocument();
    expect(trackApi.fetchTrack).toHaveBeenCalledWith('s-1');
  });

  it('announces a ready order and drops the notification opt-in', async () => {
    vi.mocked(trackApi.fetchTrack).mockResolvedValue({ restaurantName: 'Café Aurora', sessionActive: true, orders: [order('READY')] });
    renderPage();
    expect(await screen.findByText('Your order is ready for pickup!')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Notify me' })).not.toBeInTheDocument();
  });

  it('explains an invalid link', async () => {
    vi.mocked(trackApi.fetchTrack).mockRejectedValue(new trackApi.UnknownSessionError());
    renderPage();
    expect(await screen.findByText('This link is not valid')).toBeInTheDocument();
  });

  it('subscribes to push when the customer taps "Notify me"', async () => {
    vi.mocked(trackApi.fetchTrack).mockResolvedValue({ restaurantName: 'Café Aurora', sessionActive: true, orders: [order('CREATED')] });
    vi.mocked(push.subscribeToPush).mockResolvedValue('subscribed');
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Notify me' }));
    await waitFor(() => expect(push.subscribeToPush).toHaveBeenCalledWith('s-1'));
    expect(await screen.findByText(/Notifications are on/)).toBeInTheDocument();
  });

  it('tells the customer when notifications are blocked', async () => {
    vi.mocked(trackApi.fetchTrack).mockResolvedValue({ restaurantName: 'Café Aurora', sessionActive: true, orders: [order('CREATED')] });
    vi.mocked(push.subscribeToPush).mockResolvedValue('denied');
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Notify me' }));
    expect(await screen.findByText(/blocked in your browser settings/)).toBeInTheDocument();
  });

  it('shows the iPhone install hint instead of a dead button', async () => {
    vi.mocked(push.pushSupport).mockReturnValue('ios-needs-install');
    vi.mocked(trackApi.fetchTrack).mockResolvedValue({ restaurantName: 'Café Aurora', sessionActive: true, orders: [order('CREATED')] });
    renderPage();
    expect(await screen.findByText(/Add to Home Screen/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Notify me' })).not.toBeInTheDocument();
  });
});
