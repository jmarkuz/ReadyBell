import { useEffect, useState, type ReactNode } from 'react';
import { Check, CheckCircle2, Copy, Printer } from 'lucide-react';
import { isLocalOnlyUrl, printReceipt } from '@/utils/receipt';

interface Props {
  orderNumber: string;
  /** The tracking link the QR code encodes. */
  url: string;
  /** The QR code as a data URL. */
  qr: string;
  restaurantName: string;
  /** The customer already opened the link: the QR code is hidden so nobody else can scan it. */
  connected: boolean;
  /** Extra buttons (for example "New order"). */
  children?: ReactNode;
}

/**
 * The hand-over of an order's tracking link: number, QR code, print and copy. Once the customer has scanned it the code
 * is replaced by a tick (it is the customer's own link, so the next person in the queue must not be able to scan it
 * from the counter); staff can bring it back with one click if the customer needs it again.
 */
export default function OrderQrPanel({ orderNumber, url, qr, restaurantName, connected, children }: Props) {
  const [copied, setCopied] = useState(false);
  const [revealed, setRevealed] = useState(false);
  useEffect(() => { setRevealed(false); setCopied(false); }, [url]);
  useEffect(() => { if (!connected) setRevealed(false); }, [connected]);

  const hidden = connected && !revealed;

  return (
    <div className="text-center space-y-3">
      <p className="text-3xl font-semibold tracking-wide">{orderNumber}</p>

      {hidden ? (
        <div role="status" className="mx-auto w-56 h-56 rounded-lg border border-green-200 bg-green-50 dark:bg-green-950/30 dark:border-green-900 flex flex-col items-center justify-center gap-2 text-green-800 dark:text-green-300">
          <CheckCircle2 className="w-14 h-14" aria-hidden="true" />
          <p className="font-medium">Customer connected</p>
          <p className="text-xs px-4">The phone is following this order. The QR code is hidden.</p>
        </div>
      ) : (
        <img src={qr} alt={`QR code for order ${orderNumber}`} className="mx-auto w-56 h-56 rounded-lg border border-slate-200 bg-white" />
      )}

      {hidden ? (
        <button type="button" onClick={() => setRevealed(true)} className="text-sm underline text-slate-600 dark:text-slate-400">Show QR code again</button>
      ) : (
        <>
          <p className="text-sm text-slate-600 dark:text-slate-400">Show or print this code. The customer scans it to follow the order and get notified.</p>
          <p className="text-xs break-all text-slate-500">{url}</p>
        </>
      )}

      {isLocalOnlyUrl(url) && (
        <p role="alert" className="text-sm text-left rounded-lg bg-amber-50 text-amber-900 border border-amber-200 p-3">
          This link points to <strong>localhost</strong>, so a customer's phone cannot open it. Open the
          dashboard through this computer's network address (e.g. <code>npm run dev:lan</code>) or set{' '}
          <code>VITE_PUBLIC_URL</code>, then create the order again.
        </p>
      )}

      <div className="flex gap-2 justify-center flex-wrap pt-2">
        <button type="button" onClick={() => printReceipt({ restaurant: restaurantName, orderNumber, qr, url })}
          className="inline-flex items-center gap-2 h-10 px-4 rounded-full bg-stone-800 text-white text-sm font-medium"><Printer className="w-4 h-4" aria-hidden="true" />Print</button>
        <button type="button" onClick={async () => { try { await navigator.clipboard.writeText(url); setCopied(true); } catch { /* clipboard unavailable */ } }}
          className="inline-flex items-center gap-2 h-10 px-4 rounded-full border border-slate-300 text-sm font-medium">{copied ? <Check className="w-4 h-4" aria-hidden="true" /> : <Copy className="w-4 h-4" aria-hidden="true" />}{copied ? 'Copied' : 'Copy link'}</button>
        {children}
      </div>
    </div>
  );
}
