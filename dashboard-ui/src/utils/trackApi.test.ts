import { describe, it, expect, vi, afterEach } from 'vitest';
import { fetchTrack, fetchPushConfig, registerSubscription, UnknownSessionError } from './trackApi';

const respond = (status: number, body: unknown = {}) => vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status }));
afterEach(() => vi.unstubAllGlobals());

describe('trackApi', () => {
  it('maps 404 to UnknownSessionError and sends no Authorization header', async () => {
    const f = respond(404);
    vi.stubGlobal('fetch', f);
    await expect(fetchTrack('abc')).rejects.toBeInstanceOf(UnknownSessionError);
    expect(f).toHaveBeenCalledWith('/api/sessions/abc/track');
  });

  it('other failures are plain errors', async () => {
    vi.stubGlobal('fetch', respond(500));
    await expect(fetchTrack('abc')).rejects.toThrow('500');
  });

  it('push config falls back to disabled when the call fails', async () => {
    vi.stubGlobal('fetch', respond(500));
    await expect(fetchPushConfig()).resolves.toEqual({ enabled: false, publicKey: null });
  });

  it('registers a subscription with the backend field names', async () => {
    const f = respond(201);
    vi.stubGlobal('fetch', f);
    await registerSubscription('s1', { endpointUrl: 'https://p/1', p256dhKey: 'k', authKey: 'a' });
    const [url, init] = f.mock.calls[0];
    expect(url).toBe('/api/sessions/s1/subscriptions');
    expect(JSON.parse(init.body)).toEqual({ endpointUrl: 'https://p/1', p256dhKey: 'k', authKey: 'a' });
  });
});
