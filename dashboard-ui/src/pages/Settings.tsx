import { useEffect, useState, type FormEvent } from 'react';
import { counterUrl, isLocalOnlyUrl, printCounterQr, qrDataUrl } from '@/utils/receipt';
import { apiClient } from '@/utils/api';
import { useRestaurant } from '@/context/restaurantContext';
import { cardClass, inputClass, primaryButton } from '@/components/ui';

/** Restaurant admin: the name customers see in the panel header and on their tracking page. */
export default function Settings() {
  const restaurant = useRestaurant();
  const [name, setName] = useState(restaurant.name);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);

  useEffect(() => setName(restaurant.name), [restaurant.name]);

  // The counter QR: one per restaurant, shown at the pickup area
  const [counterQr, setCounterQr] = useState<{ url: string; qr: string } | null>(null);
  useEffect(() => {
    if (!restaurant.identifier) return undefined;
    let cancelled = false;
    const url = counterUrl(restaurant.identifier);
    qrDataUrl(url).then((qr) => { if (!cancelled) setCounterQr({ url, qr }); }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [restaurant.identifier]);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await apiClient.updateRestaurantConfig({ name: name.trim() });
      await restaurant.refresh();
      setMessage({ text: 'Saved.', error: false });
    } catch (err) {
      setMessage({ text: err instanceof Error ? err.message : 'Could not save.', error: true });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl text-slate-900 dark:text-white">Settings</h1>
        <p className="text-slate-600 dark:text-slate-400 mt-2">Your restaurant's public details.</p>
      </div>

      <section className={`${cardClass} max-w-xl`} aria-labelledby="restaurant-details">
        <h2 id="restaurant-details" className="text-xl mb-4">Restaurant</h2>
        <form onSubmit={save} className="space-y-4">
          <div>
            <label htmlFor="restaurant-name" className="block text-sm font-medium mb-1">Name shown to customers</label>
            <input id="restaurant-name" className={inputClass} value={name} required maxLength={100} onChange={(e) => setName(e.target.value)} />
          </div>
          {restaurant.identifier && (
            <p className="text-sm text-slate-500">Identifier: <span className="font-mono">{restaurant.identifier}</span></p>
          )}
          <div className="flex items-center gap-4">
            <button type="submit" className={primaryButton} disabled={busy || !name.trim()}>{busy ? 'Saving…' : 'Save'}</button>
            {message && <span role={message.error ? 'alert' : 'status'} className={`text-sm ${message.error ? 'text-red-600' : 'text-slate-600'}`}>{message.text}</span>}
          </div>
        </form>
      </section>

      {counterQr && (
        <section className={`${cardClass} max-w-xl`} aria-labelledby="counter-qr">
          <h2 id="counter-qr" className="text-xl mb-2">Counter QR code</h2>
          <p className="text-sm text-slate-600 dark:text-slate-400 mb-4">
            Show it at the pickup area. A customer scans it, types the order number from the receipt and gets a
            notification when the order is ready. Orders can be claimed for a few hours after they are created.
          </p>
          <img src={counterQr.qr} alt={`Counter QR code for ${restaurant.name}`} className="w-56 h-56 rounded-lg border border-slate-200 bg-white" />
          <p className="text-xs text-slate-500 mt-2 break-all">{counterQr.url}</p>
          {isLocalOnlyUrl(counterQr.url) && (
            <p role="alert" className="text-sm text-amber-700 mt-2">
              This link points at this computer, so phones cannot open it. Set VITE_PUBLIC_URL or open the dashboard through a public address.
            </p>
          )}
          <button
            type="button"
            className={`${primaryButton} mt-4`}
            onClick={() => printCounterQr({ restaurant: restaurant.name, qr: counterQr.qr, url: counterQr.url })}
          >
            Print sign
          </button>
        </section>
      )}
    </div>
  );
}
