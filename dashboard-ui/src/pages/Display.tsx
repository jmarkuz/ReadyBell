import { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { RestaurantProvider } from '@/context/RestaurantProvider';
import { useRestaurant } from '@/context/restaurantContext';
import { useDashboardOrders, type OrdersQuery } from '@/hooks/useDashboardOrders';
import { qrDataUrl, trackingUrl } from '@/utils/receipt';
import { useStaffStrings } from '@/i18n/useStaffStrings';

const HANDOVER_WINDOW_MS = 15 * 60_000;
const CLOCK_TICK_MS = 60_000;
const CONFIRMATION_MS = 4_000;

/**
 * Customer-facing screen for a second display at the counter (open it in its own window, e.g. on a tablet).
 *
 * It shows the QR code of the order staff entered last, as long as the customer has not connected yet. Once the
 * customer's phone opens the link the code disappears behind a confirmation, so nobody else can scan it. Only the
 * receipt number and the code are shown: no names, no items.
 */
function CustomerDisplay() {
  const t = useStaffStrings();
  const restaurant = useRestaurant();

  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), CLOCK_TICK_MS);
    return () => clearInterval(timer);
  }, []);

  const [connectedIds, setConnectedIds] = useState<ReadonlySet<string>>(() => new Set());
  const [confirmedOrder, setConfirmedOrder] = useState<string | null>(null);
  const [orderNumbers] = useState(() => new Map<string, string>());

  const query = useMemo<OrdersQuery>(() => ({
    page: 1,
    pageSize: 10,
    statuses: ['CREATED', 'PREPARING'],
    search: '',
    from: new Date(now - HANDOVER_WINDOW_MS),
    to: null,
    sortBy: 'created',
    sortOrder: 'desc',
  }), [now]);

  const handleConnected = useCallback((orderId: string) => {
    setConnectedIds((prev) => new Set(prev).add(orderId));
    setConfirmedOrder(orderNumbers.get(orderId) ?? '');
  }, [orderNumbers]);
  const { orders } = useDashboardOrders(query, { onOrderConnected: handleConnected });
  orders.forEach((o) => orderNumbers.set(o.id, o.orderId));

  useEffect(() => {
    if (confirmedOrder === null) return undefined;
    const timer = setTimeout(() => setConfirmedOrder(null), CONFIRMATION_MS);
    return () => clearTimeout(timer);
  }, [confirmedOrder]);

  const current = orders.find((o) => o.sessionId && !o.connected && !connectedIds.has(o.id));
  const url = current?.sessionId ? trackingUrl(current.sessionId) : null;

  const [qr, setQr] = useState<{ url: string; data: string } | null>(null);
  useEffect(() => {
    if (!url) return undefined;
    let cancelled = false;
    qrDataUrl(url).then((data) => { if (!cancelled) setQr({ url, data }); }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [url]);

  let body;
  if (confirmedOrder !== null) {
    body = (
      <div role="status" className="flex flex-col items-center gap-4 text-green-700 dark:text-green-400">
        <CheckCircle2 className="w-32 h-32" aria-hidden="true" />
        <p className="text-4xl font-semibold">{t.displayConnected}</p>
        <p className="text-xl">{t.displayNotify}</p>
      </div>
    );
  } else if (current && qr?.url === url) {
    body = (
      <div className="flex flex-col items-center gap-6">
        <p className="text-6xl font-bold tracking-wide">{current.orderId}</p>
        <img src={qr.data} alt={`QR code for order ${current.orderId}`} className="w-80 h-80 rounded-xl border border-slate-200 bg-white" />
        <p className="text-2xl">{t.displayScan}</p>
      </div>
    );
  } else {
    body = <p role="status" className="text-3xl text-slate-500">{t.displayWelcome}</p>;
  }

  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-10 p-8 text-center bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-50">
      <h1 className="text-3xl font-semibold text-slate-600 dark:text-slate-300">{restaurant.name}</h1>
      {body}
    </main>
  );
}

export default function Display() {
  return (
    <RestaurantProvider>
      <CustomerDisplay />
    </RestaurantProvider>
  );
}
