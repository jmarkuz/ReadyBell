/**
 * Public API used by the customer tracking page. Plain `fetch` on purpose: customers never carry a
 * staff token, and a leftover staff token on a shared device must not be sent along.
 */

export interface TrackedOrder {
  orderNumber: string;
  status: 'CREATED' | 'IN_PROGRESS' | 'READY' | 'COMPLETED' | 'CANCELLED';
  estimatedReadyAt: string | null;
  estimatedWaitMinutes: number | null;
  items: Array<{ name: string; quantity: number }>;
  updatedAt: string;
}

export interface TrackResult {
  restaurantName: string;
  sessionActive: boolean;
  orders: TrackedOrder[];
}

export interface PushConfig {
  enabled: boolean;
  publicKey: string | null;
}

/** Thrown for a 404: the link does not belong to any session. */
export class UnknownSessionError extends Error {
  constructor() {
    super('Unknown session');
    this.name = 'UnknownSessionError';
  }
}

export async function fetchTrack(sessionId: string): Promise<TrackResult> {
  const res = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/track`);
  if (res.status === 404) throw new UnknownSessionError();
  if (!res.ok) throw new Error(`Track request failed (${res.status})`);
  return res.json();
}

/** Thrown when the counter claim finds no order to hand out (the server never says why). */
export class OrderNotFoundError extends Error {
  constructor() {
    super('Order not found');
    this.name = 'OrderNotFoundError';
  }
}

/** Thrown for a 429: too many guesses from this connection. */
export class TooManyAttemptsError extends Error {
  constructor() {
    super('Too many attempts');
    this.name = 'TooManyAttemptsError';
  }
}

/** The restaurant a counter QR belongs to; null when the link is not valid. */
export async function fetchCounterRestaurant(identifier: string): Promise<string | null> {
  const res = await fetch(`/api/restaurants/${encodeURIComponent(identifier)}/public`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Restaurant request failed (${res.status})`);
  return (await res.json()).name as string;
}

/** Turns the number printed on the receipt into the tracking session of that order. */
export async function claimOrder(identifier: string, orderNumber: number): Promise<string> {
  const res = await fetch(`/api/restaurants/${encodeURIComponent(identifier)}/claim`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ orderNumber }),
  });
  if (res.status === 404 || res.status === 400) throw new OrderNotFoundError();
  if (res.status === 429) throw new TooManyAttemptsError();
  if (!res.ok) throw new Error(`Claim failed (${res.status})`);
  return (await res.json()).sessionId as string;
}

export async function fetchPushConfig(): Promise<PushConfig> {
  const res = await fetch('/api/push/config');
  if (!res.ok) return { enabled: false, publicKey: null };
  return res.json();
}

export async function registerSubscription(
  sessionId: string,
  sub: { endpointUrl: string; p256dhKey: string; authKey: string }
): Promise<void> {
  const res = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/subscriptions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(sub),
  });
  if (!res.ok) throw new Error(`Subscription failed (${res.status})`);
}
