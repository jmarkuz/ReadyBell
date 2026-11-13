import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { customerStrings } from '@/i18n/customer';
import { CustomerShell as Shell } from '@/components/CustomerShell';
import { displayFont } from '@/components/customerStyles';
import { parseOrderNumber } from '@/utils/orderNumber';
import { claimOrder, fetchCounterRestaurant, OrderNotFoundError, TooManyAttemptsError } from '@/utils/trackApi';

type Problem = 'invalidNumber' | 'notFound' | 'tooMany' | 'error';

/**
 * Counter QR page (`/r/:identifier`): one QR per restaurant, shown at the pickup area. The customer types the number
 * from the receipt and is sent on to the tracking page of that order. No sign-in.
 */
export default function CounterClaim() {
  const { identifier = '' } = useParams();
  const navigate = useNavigate();
  const t = useMemo(() => customerStrings(), []);
  const [restaurant, setRestaurant] = useState<string | null | undefined>(undefined);   // undefined: loading, null: invalid
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<Problem | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchCounterRestaurant(identifier)
      .then((name) => { if (!cancelled) setRestaurant(name); })
      .catch(() => { if (!cancelled) setRestaurant(null); });
    return () => { cancelled = true; };
  }, [identifier]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const number = parseOrderNumber(value);
    if (number === null) {
      setProblem('invalidNumber');
      return;
    }
    setBusy(true);
    setProblem(null);
    try {
      const sessionId = await claimOrder(identifier, number);
      navigate(`/track/${sessionId}`, { replace: true });
    } catch (err) {
      setProblem(err instanceof OrderNotFoundError ? 'notFound' : err instanceof TooManyAttemptsError ? 'tooMany' : 'error');
      setBusy(false);
    }
  };

  const messages: Record<Problem, string> = {
    invalidNumber: t.counterInvalidNumber,
    notFound: t.counterNotFound,
    tooMany: t.counterTooMany,
    error: t.counterError,
  };

  if (restaurant === undefined) {
    return <Shell><p className="text-stone-600" role="status">{t.counterBusy}</p></Shell>;
  }
  if (restaurant === null) {
    return (
      <Shell>
        <h2 className="text-2xl mt-8" style={displayFont}>{t.counterInvalidLink}</h2>
        <p className="mt-2 text-stone-600">{t.counterInvalidLinkHint}</p>
      </Shell>
    );
  }

  return (
    <Shell restaurantName={restaurant}>
      <h2 className="text-2xl mt-4" style={displayFont}>{t.counterTitle}</h2>
      <p className="mt-2 text-stone-600">{t.counterHint}</p>
      <form onSubmit={submit} className="mt-6 rounded-2xl bg-white border border-stone-200 p-5 shadow-sm" noValidate>
        <label htmlFor="order-number" className="block text-sm text-stone-600 mb-2">{t.counterLabel}</label>
        <input
          id="order-number"
          className="w-full rounded-xl border border-stone-300 px-4 py-3 text-2xl tracking-wide focus:outline-none focus:ring-2 focus:ring-stone-800"
          inputMode="numeric"
          autoComplete="off"
          autoFocus
          placeholder="101"
          value={value}
          aria-invalid={problem === 'invalidNumber' || problem === 'notFound'}
          aria-describedby={problem ? 'order-number-problem' : undefined}
          onChange={(e) => setValue(e.target.value)}
        />
        {problem && <p id="order-number-problem" role="alert" className="mt-3 text-sm text-red-700">{messages[problem]}</p>}
        <button
          type="submit"
          disabled={busy || !value.trim()}
          className="mt-4 w-full rounded-full bg-stone-900 text-white py-3 text-lg disabled:opacity-50"
        >
          {busy ? t.counterBusy : t.counterSubmit}
        </button>
      </form>
    </Shell>
  );
}
