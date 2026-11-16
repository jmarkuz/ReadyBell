import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import OrderQrPanel from './OrderQrPanel';
import { qrDataUrl, trackingUrl } from '@/utils/receipt';

interface Props {
  order: { orderNumber: string; sessionId: string };
  connected: boolean;
  restaurantName: string;
  onClose: () => void;
}

/** Shows the QR code of an existing order, for a customer who was not handed it when the order was entered. */
export default function OrderQrDialog({ order, connected, restaurantName, onClose }: Props) {
  const url = trackingUrl(order.sessionId);
  const [qr, setQr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    qrDataUrl(url).then((data) => { if (!cancelled) setQr(data); }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [url]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50" role="presentation"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" aria-labelledby="order-qr-title"
        className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-2xl bg-white dark:bg-slate-900 shadow-xl p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 id="order-qr-title" className="text-2xl">Order QR code</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="p-2 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800"><X className="w-5 h-5" aria-hidden="true" /></button>
        </div>
        {qr ? (
          <OrderQrPanel orderNumber={order.orderNumber} url={url} qr={qr} restaurantName={restaurantName} connected={connected} />
        ) : (
          <p role="status" className="text-center text-slate-500">Preparing the code…</p>
        )}
      </div>
    </div>
  );
}
