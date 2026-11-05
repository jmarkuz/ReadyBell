import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NewOrderDialog from './NewOrderDialog';
import { apiClient } from '@/utils/api';

vi.mock('@/utils/api', async (orig) => ({ ...(await orig<typeof import('@/utils/api')>()), apiClient: { createOrder: vi.fn() } }));
vi.mock('@/utils/receipt', () => ({
  trackingUrl: (id: string) => (globalThis as { __url?: string }).__url ?? `https://shop.example/track/${id}`,
  isLocalOnlyUrl: (u: string) => u.includes('localhost'),
  qrDataUrl: vi.fn().mockResolvedValue('data:image/png;base64,AAAA'),
  printReceipt: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

describe('NewOrderDialog', () => {
  it('renders nothing while closed', () => {
    const { container } = render(<NewOrderDialog open={false} onClose={vi.fn()} onCreated={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('creates the order without a session id and shows the receipt QR', async () => {
    vi.mocked(apiClient.createOrder).mockResolvedValue({
      id: 'o1', orderId: '#101', sessionId: 'sess-9', status: 'CREATED', version: 0, createdAt: '', updatedAt: '',
    });
    const onCreated = vi.fn();
    render(<NewOrderDialog open onClose={vi.fn()} onCreated={onCreated} />);

    await userEvent.type(screen.getByLabelText(/Customer name/), 'Anna');
    await userEvent.type(screen.getByLabelText('Item 1 name'), 'Latte');
    await userEvent.click(screen.getByRole('button', { name: '15' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create order' }));

    expect(apiClient.createOrder).toHaveBeenCalledWith({
      customerName: 'Anna', estimatedMinutes: 15, items: [{ name: 'Latte', quantity: 1 }],
    });
    expect(await screen.findByText('#101')).toBeInTheDocument();
    expect(screen.getByAltText('QR code for order #101')).toBeInTheDocument();
    expect(screen.getByText('https://shop.example/track/sess-9')).toBeInTheDocument();
    expect(onCreated).toHaveBeenCalled();
  });

  it('warns when the QR link points to localhost', async () => {
    (globalThis as { __url?: string }).__url = 'http://localhost:5173/track/s1';
    vi.mocked(apiClient.createOrder).mockResolvedValue({
      id: 'o1', orderId: '#101', sessionId: 's1', status: 'CREATED', version: 0, createdAt: '', updatedAt: '',
    });
    render(<NewOrderDialog open onClose={vi.fn()} onCreated={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Create order' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot open it/);
    delete (globalThis as { __url?: string }).__url;
  });

  it('skips blank item rows and shows server errors', async () => {
    vi.mocked(apiClient.createOrder).mockRejectedValue(new Error('Boom'));
    render(<NewOrderDialog open onClose={vi.fn()} onCreated={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Create order' }));
    expect(apiClient.createOrder).toHaveBeenCalledWith(expect.objectContaining({ items: [] }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Boom');
  });

  it('closes on Escape', async () => {
    const onClose = vi.fn();
    render(<NewOrderDialog open onClose={onClose} onCreated={vi.fn()} />);
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });
});
