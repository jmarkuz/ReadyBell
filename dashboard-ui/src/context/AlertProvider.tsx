import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AlertContext, type NewOrderAlerts } from './alertContext';
import {
  playNewOrderSound,
  readSoundPreference,
  unlockAudio,
  vibrateForNewOrder,
  writeSoundPreference,
} from '@/utils/newOrderAlert';

/**
 * New-order alerts for the staff panel:
 * - a polite live-region announcement for screen readers (always),
 * - a counter in the tab title while the page is in the background (always),
 * - a chime and vibration (only when staff turned the sound on in the header).
 */
export function AlertProvider({ children }: { children: ReactNode }) {
  const [soundEnabled, setSoundState] = useState(readSoundPreference);
  const [announcement, setAnnouncement] = useState('');
  const [unseen, setUnseen] = useState(0);
  const baseTitle = useRef(typeof document === 'undefined' ? '' : document.title);

  const setSoundEnabled = useCallback((on: boolean) => {
    setSoundState(on);
    writeSoundPreference(on);
    if (on) {
      unlockAudio();           // this click is the user gesture browsers require
      playNewOrderSound();     // also a preview of what staff will hear
      vibrateForNewOrder();
    }
  }, []);

  // A remembered "on" still needs one interaction with the page before the browser lets us make noise
  useEffect(() => {
    if (!soundEnabled) return undefined;
    const unlock = () => unlockAudio();
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
    return () => {
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
  }, [soundEnabled]);

  const notifyNewOrder = useCallback((orderNumber?: string) => {
    setAnnouncement(orderNumber ? `New order ${orderNumber}` : 'New order');
    if (document.hidden || !document.hasFocus()) setUnseen((n) => n + 1);
    if (soundEnabled) {
      playNewOrderSound();
      vibrateForNewOrder();
    }
  }, [soundEnabled]);

  // "(2) ReadyBell Dashboard" while orders came in behind the user's back; cleared when they come back
  useEffect(() => {
    document.title = unseen > 0 ? `(${unseen}) ${baseTitle.current}` : baseTitle.current;
  }, [unseen]);
  useEffect(() => {
    const back = () => { if (!document.hidden) setUnseen(0); };
    document.addEventListener('visibilitychange', back);
    window.addEventListener('focus', back);
    return () => {
      document.removeEventListener('visibilitychange', back);
      window.removeEventListener('focus', back);
    };
  }, []);

  const value = useMemo<NewOrderAlerts>(
    () => ({ soundEnabled, setSoundEnabled, notifyNewOrder }),
    [soundEnabled, setSoundEnabled, notifyNewOrder]
  );

  return (
    <AlertContext.Provider value={value}>
      {children}
      <div role="status" aria-live="polite" aria-atomic="true" className="sr-only">{announcement}</div>
    </AlertContext.Provider>
  );
}
