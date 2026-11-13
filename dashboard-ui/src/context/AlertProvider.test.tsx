import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AlertProvider } from './AlertProvider';
import { useNewOrderAlerts } from './alertContext';
import * as alert from '@/utils/newOrderAlert';

vi.mock('@/utils/newOrderAlert', async (orig) => ({
  ...(await orig<typeof alert>()),
  playNewOrderSound: vi.fn(),
  vibrateForNewOrder: vi.fn(),
  unlockAudio: vi.fn(),
}));

function Probe() {
  const { soundEnabled, setSoundEnabled, notifyNewOrder } = useNewOrderAlerts();
  return (
    <>
      <button type="button" onClick={() => setSoundEnabled(!soundEnabled)}>sound {soundEnabled ? 'on' : 'off'}</button>
      <button type="button" onClick={() => notifyNewOrder('#7')}>new order</button>
    </>
  );
}

const setHidden = (hidden: boolean, focused = !hidden) => {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
  vi.spyOn(document, 'hasFocus').mockReturnValue(focused);
};

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  document.title = 'ReadyBell Dashboard';
  setHidden(false);
});
afterEach(() => vi.restoreAllMocks());

describe('AlertProvider', () => {
  it('stays silent while the sound is off, but still announces the order to screen readers', async () => {
    const user = userEvent.setup();
    render(<AlertProvider><Probe /></AlertProvider>);

    await user.click(screen.getByRole('button', { name: 'new order' }));

    expect(alert.playNewOrderSound).not.toHaveBeenCalled();
    expect(alert.vibrateForNewOrder).not.toHaveBeenCalled();
    expect(screen.getByRole('status')).toHaveTextContent('New order #7');
  });

  it('chimes and vibrates once the sound is on, and turning it on plays a preview', async () => {
    const user = userEvent.setup();
    render(<AlertProvider><Probe /></AlertProvider>);

    await user.click(screen.getByRole('button', { name: 'sound off' }));
    expect(alert.unlockAudio).toHaveBeenCalled();
    expect(alert.playNewOrderSound).toHaveBeenCalledTimes(1);   // the preview

    await user.click(screen.getByRole('button', { name: 'new order' }));
    expect(alert.playNewOrderSound).toHaveBeenCalledTimes(2);
    expect(alert.vibrateForNewOrder).toHaveBeenCalledTimes(2);
  });

  it('remembers the choice and turning it off silences new orders again', async () => {
    const user = userEvent.setup();
    const first = render(<AlertProvider><Probe /></AlertProvider>);
    await user.click(screen.getByRole('button', { name: 'sound off' }));
    first.unmount();

    render(<AlertProvider><Probe /></AlertProvider>);
    expect(screen.getByRole('button', { name: 'sound on' })).toBeInTheDocument();   // restored from localStorage

    await user.click(screen.getByRole('button', { name: 'sound on' }));
    vi.mocked(alert.playNewOrderSound).mockClear();
    await user.click(screen.getByRole('button', { name: 'new order' }));
    expect(alert.playNewOrderSound).not.toHaveBeenCalled();
  });

  it('counts orders in the tab title while the page is in the background and clears it on return', async () => {
    const user = userEvent.setup();
    render(<AlertProvider><Probe /></AlertProvider>);

    setHidden(true);
    await user.click(screen.getByRole('button', { name: 'new order' }));
    await user.click(screen.getByRole('button', { name: 'new order' }));
    expect(document.title).toBe('(2) ReadyBell Dashboard');

    setHidden(false);
    act(() => { document.dispatchEvent(new Event('visibilitychange')); });
    expect(document.title).toBe('ReadyBell Dashboard');
  });

  it('does not touch the title while the page is in front', async () => {
    const user = userEvent.setup();
    render(<AlertProvider><Probe /></AlertProvider>);

    await user.click(screen.getByRole('button', { name: 'new order' }));

    expect(document.title).toBe('ReadyBell Dashboard');
  });
});
