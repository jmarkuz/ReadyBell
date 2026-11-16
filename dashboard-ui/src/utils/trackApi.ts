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
