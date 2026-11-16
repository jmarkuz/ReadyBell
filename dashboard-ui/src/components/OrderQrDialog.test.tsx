import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OrderQrDialog from './OrderQrDialog';

vi.mock('@/utils/receipt', () => ({
  trackingUrl: (id: string) => `https://shop.example/track/${id}`,
  isLocalOnlyUrl: () => false,
  qrDataUrl: vi.fn().mockResolvedValue('data:image/png;base64,AAAA'),
  printReceipt: vi.fn(),
}));

const order = { orderNumber: '#7', sessionId: 'sess-7' };

describe('OrderQrDialog', () => {
  it('shows the QR code of an existing order', async () => {
    render(<OrderQrDialog order={order} connected={false} restaurantName="Cafe" onClose={vi.fn()} />);
    expect(await screen.findByAltText('QR code for order #7')).toBeInTheDocument();
    expect(screen.getByText('https://shop.example/track/sess-7')).toBeInTheDocument();
  });

  it('hides the code once the customer is connected and brings it back on request', async () => {
    const user = userEvent.setup();
    render(<OrderQrDialog order={order} connected restaurantName="Cafe" onClose={vi.fn()} />);
    expect(await screen.findByText('Customer connected')).toBeInTheDocument();
    expect(screen.queryByAltText('QR code for order #7')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Show QR code again' }));
    expect(screen.getByAltText('QR code for order #7')).toBeInTheDocument();
  });

  it('hides the code when the connection arrives while it is open', async () => {
    const { rerender } = render(<OrderQrDialog order={order} connected={false} restaurantName="Cafe" onClose={vi.fn()} />);
    expect(await screen.findByAltText('QR code for order #7')).toBeInTheDocument();
    rerender(<OrderQrDialog order={order} connected restaurantName="Cafe" onClose={vi.fn()} />);
    expect(screen.queryByAltText('QR code for order #7')).not.toBeInTheDocument();
    expect(screen.getByText('Customer connected')).toBeInTheDocument();
  });

  it('closes on Escape', async () => {
    const onClose = vi.fn();
    render(<OrderQrDialog order={order} connected={false} restaurantName="Cafe" onClose={onClose} />);
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });
});
