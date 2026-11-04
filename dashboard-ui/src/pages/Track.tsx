import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Bell, BellRing, CheckCircle2, Clock, Coffee } from 'lucide-react';
import { customerStrings } from '@/i18n/customer';
import { fetchTrack, UnknownSessionError, type TrackedOrder, type TrackResult } from '@/utils/trackApi';
import { pushSupport, registerServiceWorker, subscribeToPush } from '@/utils/push';

const POLL_MS = 10_000;
const STEPS: TrackedOrder['status'][] = ['CREATED', 'IN_PROGRESS', 'READY'];
type NotifyState = 'idle' | 'busy' | 'on' | 'denied' | 'error' | 'unavailable';

function notifiedKey(sessionId: string) {
  return `readybell-notify-${sessionId}`;
}

/**
 * Customer tracking page, opened from the QR code on the receipt (`/track/:sessionId`).
 * No sign-in: the unguessable session id in the link is the credential.
 */
export default function Track() {
  const { sessionId = '' } = useParams();
  const t = useMemo(() => customerStrings(), []);
  const [data, setData] = useState<TrackResult | null>(null);
  const [invalid, setInvalid] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [live, setLive] = useState(false);
  const [notify, setNotify] = useState<NotifyState>(() => {
    try {
      return localStorage.getItem(notifiedKey(sessionId)) ? 'on' : 'idle';
    } catch {
      return 'idle';
    }
  });
  const support = useMemo(() => pushSupport(), []);
  const mounted = useRef(true);

  const load = useCallback(async () => {
    try {
      const result = await fetchTrack(sessionId);
      if (!mounted.current) return;
      setData(result);
      setLoadFailed(false);
    } catch (e) {
      if (!mounted.current) return;
      if (e instanceof UnknownSessionError) setInvalid(true);
      else setLoadFailed(true);
    }
  }, [sessionId]);

  // initial load + polling fallback
  useEffect(() => {
    mounted.current = true;
    void registerServiceWorker();
    void load();
    const timer = setInterval(() => void load(), POLL_MS);
    return () => {
      mounted.current = false;
      clearInterval(timer);
    };
  }, [load]);

  // live updates: refetch whenever the server reports a status change
  useEffect(() => {
    if (!sessionId || typeof EventSource === 'undefined') return;
    const es = new EventSource(`/api/sessions/${encodeURIComponent(sessionId)}/orders/stream`);
    es.onopen = () => setLive(true);
    es.onerror = () => setLive(false);
    es.addEventListener('order-status-updated', () => void load());
    return () => es.close();
  }, [sessionId, load]);

  useEffect(() => {
    if (data?.restaurantName) document.title = `${data.restaurantName} · ReadyBell`;
  }, [data?.restaurantName]);

  const enableNotifications = useCallback(async () => {
    setNotify('busy');
    try {
      const outcome = await subscribeToPush(sessionId);
      if (outcome === 'subscribed') {
        try {
          localStorage.setItem(notifiedKey(sessionId), '1');
        } catch {
          /* private mode: the button simply shows again next visit */
        }
        setNotify('on');
      } else {
        setNotify(outcome === 'denied' ? 'denied' : 'unavailable');
      }
    } catch {
      setNotify('error');
    }
  }, [sessionId]);

  if (invalid) {
    return (
      <Shell>
        <div className="rounded-2xl bg-white border border-stone-200 p-8 text-center shadow-sm">
          <h1 className="text-3xl mb-2" style={displayFont}>{t.invalidLink}</h1>
          <p className="text-stone-600">{t.invalidLinkHint}</p>
        </div>
      </Shell>
    );
  }

  const anyReady = data?.orders.some((o) => o.status === 'READY') ?? false;
  const allDone = !!data && data.orders.length > 0 && data.orders.every((o) => o.status === 'COMPLETED' || o.status === 'CANCELLED');
  const pushUsable = support === 'supported' || support === 'ios-needs-install';

  return (
    <Shell restaurantName={data?.restaurantName} live={live} liveLabel={live ? t.live : t.offline}>
      {!data && !loadFailed && <p className="text-center text-stone-500 py-16" role="status">{t.loading}</p>}
      {loadFailed && !data && <p className="text-center text-stone-500 py-16" role="alert">{t.loadError}</p>}

      {anyReady && (
        <div className="rounded-2xl bg-stone-900 text-white p-5 mb-4 flex items-center gap-3" role="status">
          <CheckCircle2 className="w-7 h-7 shrink-0" aria-hidden="true" />
          <p className="text-lg font-medium">{t.readyBanner}</p>
        </div>
      )}

      {data && data.orders.length === 0 && <p className="text-center text-stone-500 py-12">{t.noOrders}</p>}

      <div className="space-y-4">
        {data?.orders.map((o) => <OrderCard key={o.orderNumber} order={o} t={t} />)}
      </div>

      {data && !anyReady && !allDone && pushUsable && (
        <section className="mt-6 rounded-2xl bg-white border border-stone-200 p-5 shadow-sm" aria-label={t.notifyTitle}>
          <div className="flex items-start gap-3">
            {notify === 'on' ? <BellRing className="w-6 h-6 mt-0.5" aria-hidden="true" /> : <Bell className="w-6 h-6 mt-0.5" aria-hidden="true" />}
            <div className="flex-1">
              <h2 className="text-xl" style={displayFont}>{t.notifyTitle}</h2>
              {notify === 'on' ? (
                <p className="text-stone-600 mt-1">{t.notifyOn}</p>
              ) : (
                <>
                  <p className="text-stone-600 mt-1">{t.notifyHint}</p>
                  {support === 'ios-needs-install' && <p className="text-stone-600 mt-2 text-sm">{t.iosHint}</p>}
                  {support === 'supported' && (
                    <button
                      type="button"
                      onClick={enableNotifications}
                      disabled={notify === 'busy'}
                      className="mt-3 h-11 px-6 rounded-full bg-stone-900 text-white font-medium disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-stone-900 focus:ring-offset-2"
                    >
                      {t.notifyButton}
                    </button>
                  )}
                  {notify === 'denied' && <p className="text-stone-600 mt-2 text-sm" role="alert">{t.notifyDenied}</p>}
                  {notify === 'error' && <p className="text-red-700 mt-2 text-sm" role="alert">{t.notifyError}</p>}
                  {notify === 'unavailable' && <p className="text-stone-600 mt-2 text-sm" role="alert">{t.notifyUnsupported}</p>}
                </>
              )}
            </div>
          </div>
        </section>
      )}
      {data && !anyReady && !allDone && support === 'unsupported' && (
        <p className="mt-6 text-center text-sm text-stone-500">{t.notifyUnsupported}</p>
      )}
    </Shell>
  );
}

const displayFont = { fontFamily: 'var(--font-display)', fontWeight: 300, letterSpacing: '-0.02em' } as const;

function Shell({ children, restaurantName, live, liveLabel }: {
  children: React.ReactNode; restaurantName?: string; live?: boolean; liveLabel?: string;
}) {
  return (
    <div className="min-h-screen bg-[#f5f5f5] text-stone-900">
      <header className="px-4 pt-6 pb-4 max-w-md mx-auto flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-stone-800 text-white flex items-center justify-center" aria-hidden="true">
          <Coffee className="w-5 h-5" />
        </div>
        <h1 className="text-2xl flex-1 truncate" style={displayFont}>{restaurantName || 'ReadyBell'}</h1>
        {liveLabel && (
          <span className="text-xs text-stone-600 flex items-center gap-1.5" role="status">
            <span className={`w-2 h-2 rounded-full ${live ? 'bg-green-500' : 'bg-stone-400'}`} aria-hidden="true" />
            {liveLabel}
          </span>
        )}
      </header>
      <main className="px-4 pb-12 max-w-md mx-auto">{children}</main>
    </div>
  );
}

function OrderCard({ order, t }: { order: TrackedOrder; t: ReturnType<typeof customerStrings> }) {
  const cancelled = order.status === 'CANCELLED';
  const stepIndex = order.status === 'COMPLETED' ? STEPS.length : STEPS.indexOf(order.status);
  const showWait = (order.status === 'CREATED' || order.status === 'IN_PROGRESS') && order.estimatedWaitMinutes !== null;
  return (
    <article className="rounded-2xl bg-white border border-stone-200 p-5 shadow-sm" aria-label={`${t.orderLabel} ${order.orderNumber}`}>
      <div className="flex items-baseline justify-between">
        <span className="text-sm text-stone-500">{t.orderLabel}</span>
        <span className="text-2xl font-semibold tracking-wide">{order.orderNumber}</span>
      </div>
      <p className="text-2xl mt-3" style={displayFont}>{t.status[order.status] ?? order.status}</p>

      {!cancelled && (
        <ol className="flex gap-1.5 mt-4" aria-hidden="true">
          {STEPS.map((s, i) => (
            <li key={s} className={`h-1.5 flex-1 rounded-full ${i <= stepIndex ? 'bg-stone-900' : 'bg-stone-200'}`} />
          ))}
        </ol>
      )}

      {showWait && (
        <p className="mt-3 flex items-center gap-2 text-stone-600">
          <Clock className="w-4 h-4" aria-hidden="true" />
          {t.waitAbout(order.estimatedWaitMinutes ?? 0)}
        </p>
      )}

      {order.items.length > 0 && (
        <ul className="mt-4 pt-4 border-t border-stone-100 text-stone-700 space-y-1">
          {order.items.map((i, idx) => (
            <li key={idx}>{i.quantity > 1 ? `${i.quantity}× ` : ''}{i.name}</li>
          ))}
        </ul>
      )}
    </article>
  );
}
