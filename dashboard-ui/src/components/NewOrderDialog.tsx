import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Check, Copy, Plus, Printer, X } from 'lucide-react';
import { apiClient } from '@/utils/api';
import { isLocalOnlyUrl, printReceipt, qrDataUrl, trackingUrl } from '@/utils/receipt';
import { useRestaurant } from '@/context/restaurantContext';

interface Line { name: string; quantity: number }
interface Created { orderNumber: string; url: string; qr: string }

interface Props {
  open: boolean;
  onClose: () => void;
  /** Called after an order was created so the list can refresh. */
  onCreated: () => void;
}

const ETA_CHIPS = [5, 10, 15, 20];
const emptyLine = (): Line => ({ name: '', quantity: 1 });

/**
 * Staff order entry (US-S001). Creates the order — the server starts the customer session — and
 * then shows the receipt QR code that opens the customer's tracking page.
 */
export default function NewOrderDialog({ open, onClose, onCreated }: Props) {
  const restaurant = useRestaurant();
  const [customer, setCustomer] = useState('');
  const [lines, setLines] = useState<Line[]>([emptyLine()]);
  const [minutes, setMinutes] = useState(10);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<Created | null>(null);
  const [copied, setCopied] = useState(false);
  const firstField = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    setCustomer(''); setLines([emptyLine()]); setMinutes(10); setError(null); setCreated(null); setCopied(false);
    const t = setTimeout(() => firstField.current?.focus(), 0);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => { clearTimeout(t); document.removeEventListener('keydown', onKey); };
  }, [open, onClose]);

  if (!open) return null;

  const updateLine = (i: number, patch: Partial<Line>) =>
    setLines((ls) => ls.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const items = lines.map((l) => ({ name: l.name.trim(), quantity: l.quantity })).filter((l) => l.name);
    setBusy(true);
    setError(null);
    try {
      const order = await apiClient.createOrder({
        customerName: customer.trim() || undefined,
        estimatedMinutes: minutes,
        items,
      });
      const url = trackingUrl(order.sessionId);
      setCreated({ orderNumber: order.orderId, url, qr: await qrDataUrl(url) });
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the order.');
    } finally {
      setBusy(false);
    }
  };

  const input = 'w-full h-10 px-3 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" aria-labelledby="new-order-title" className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl bg-white dark:bg-slate-900 shadow-xl p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 id="new-order-title" className="text-2xl">{created ? 'Order created' : 'New order'}</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800"><X className="w-5 h-5" aria-hidden="true" /></button>
        </div>

        {created ? (
          <div className="text-center space-y-3">
            <p className="text-3xl font-semibold tracking-wide">{created.orderNumber}</p>
            <img src={created.qr} alt={`QR code for order ${created.orderNumber}`} className="mx-auto w-56 h-56 rounded-lg border border-slate-200" />
            <p className="text-sm text-slate-600 dark:text-slate-400">Show or print this code. The customer scans it to follow the order and get notified.</p>
            <p className="text-xs break-all text-slate-500">{created.url}</p>
            {isLocalOnlyUrl(created.url) && (
              <p role="alert" className="text-sm text-left rounded-lg bg-amber-50 text-amber-900 border border-amber-200 p-3">
                This link points to <strong>localhost</strong>, so a customer's phone cannot open it. Open the
                dashboard through this computer's network address (e.g. <code>npm run dev:lan</code>) or set{' '}
                <code>VITE_PUBLIC_URL</code>, then create the order again.
              </p>
            )}
            <div className="flex gap-2 justify-center flex-wrap pt-2">
              <button type="button" onClick={() => printReceipt({ restaurant: restaurant.name, orderNumber: created.orderNumber, qr: created.qr, url: created.url })}
                className="inline-flex items-center gap-2 h-10 px-4 rounded-full bg-stone-800 text-white text-sm font-medium"><Printer className="w-4 h-4" aria-hidden="true" />Print</button>
              <button type="button" onClick={async () => { try { await navigator.clipboard.writeText(created.url); setCopied(true); } catch { /* clipboard unavailable */ } }}
                className="inline-flex items-center gap-2 h-10 px-4 rounded-full border border-slate-300 text-sm font-medium">{copied ? <Check className="w-4 h-4" aria-hidden="true" /> : <Copy className="w-4 h-4" aria-hidden="true" />}{copied ? 'Copied' : 'Copy link'}</button>
              <button type="button" onClick={() => { setCreated(null); setCustomer(''); setLines([emptyLine()]); }}
                className="h-10 px-4 rounded-full border border-slate-300 text-sm font-medium">New order</button>
            </div>
          </div>
        ) : (
          <form onSubmit={submit} className="space-y-4">
            <div>
              <label htmlFor="no-customer" className="block text-sm font-medium mb-1">Customer name <span className="text-slate-500 font-normal">(optional)</span></label>
              <input id="no-customer" ref={firstField} className={input} value={customer} maxLength={80} onChange={(e) => setCustomer(e.target.value)} />
            </div>

            <fieldset className="space-y-2">
              <legend className="text-sm font-medium mb-1">Items</legend>
              {lines.map((l, i) => (
                <div key={i} className="flex gap-2">
                  <input aria-label={`Item ${i + 1} name`} className={input} placeholder="e.g. Latte" value={l.name} maxLength={100} onChange={(e) => updateLine(i, { name: e.target.value })} />
                  <input aria-label={`Item ${i + 1} quantity`} className={`${input} !w-20`} type="number" min={1} max={99} value={l.quantity} onChange={(e) => updateLine(i, { quantity: Math.min(99, Math.max(1, Number(e.target.value) || 1)) })} />
                  {lines.length > 1 && <button type="button" aria-label={`Remove item ${i + 1}`} onClick={() => setLines((ls) => ls.filter((_, idx) => idx !== i))} className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800"><X className="w-4 h-4" aria-hidden="true" /></button>}
                </div>
              ))}
              <button type="button" onClick={() => setLines((ls) => [...ls, emptyLine()])} className="inline-flex items-center gap-1 text-sm font-medium text-slate-700 dark:text-slate-300"><Plus className="w-4 h-4" aria-hidden="true" />Add item</button>
            </fieldset>

            <fieldset>
              <legend className="text-sm font-medium mb-1">Ready in (minutes)</legend>
              <div className="flex gap-2 items-center flex-wrap">
                {ETA_CHIPS.map((m) => (
                  <button key={m} type="button" aria-pressed={minutes === m} onClick={() => setMinutes(m)}
                    className={`h-9 px-4 rounded-full text-sm font-medium border ${minutes === m ? 'bg-stone-800 text-white border-stone-800' : 'border-slate-300'}`}>{m}</button>
                ))}
                <input aria-label="Custom minutes" className={`${input} !w-20`} type="number" min={0} max={1440} value={minutes} onChange={(e) => setMinutes(Math.min(1440, Math.max(0, Number(e.target.value) || 0)))} />
              </div>
            </fieldset>

            {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
            <button type="submit" disabled={busy} className="w-full h-11 rounded-full bg-stone-800 text-white font-medium disabled:opacity-60">{busy ? 'Creating…' : 'Create order'}</button>
          </form>
        )}
      </div>
    </div>
  );
}
