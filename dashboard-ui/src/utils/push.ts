import { fetchPushConfig, registerSubscription } from '@/utils/trackApi';

export type PushSupport = 'supported' | 'unsupported' | 'ios-needs-install';

/** Whether this browser can do Web Push, and if iOS Safari, whether the page must be installed first. */
export function pushSupport(): PushSupport {
  const hasApis = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  if (hasApis) return 'supported';
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
  return ios ? 'ios-needs-install' : 'unsupported';
}

export type SubscribeOutcome = 'subscribed' | 'denied' | 'unavailable';

function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const raw = atob(padded.replace(/-/g, '+').replace(/_/g, '/'));
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

function toBase64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) return '';
  let binary = '';
  new Uint8Array(buffer).forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Asks for permission, subscribes this browser to Web Push and registers it for the session. */
export async function subscribeToPush(sessionId: string): Promise<SubscribeOutcome> {
  const config = await fetchPushConfig();
  if (!config.enabled || !config.publicKey) return 'unavailable';

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return 'denied';

  const registration = await navigator.serviceWorker.ready;
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(config.publicKey),
    }));

  await registerSubscription(sessionId, {
    endpointUrl: subscription.endpoint,
    p256dhKey: toBase64Url(subscription.getKey('p256dh')),
    authKey: toBase64Url(subscription.getKey('auth')),
  });
  return 'subscribed';
}

export async function registerServiceWorker(): Promise<void> {
  if (!('serviceWorker' in navigator)) return;
  try {
    await navigator.serviceWorker.register('/sw.js');
  } catch (e) {
    console.warn('[push] service worker registration failed', e);
  }
}
