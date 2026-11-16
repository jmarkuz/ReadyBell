import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { apiClient } from './api';
import { tokenManager } from './TokenManager';

/** A well-formed, unexpired token (the signature is never checked in the browser). */
function fakeJwt(): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: 'admin', exp: Math.floor(Date.now() / 1000) + 3600 })}.sig`;
}

describe('apiClient.logout', () => {
  const fetchMock = vi.fn();
  const token = fakeJwt();

  beforeEach(() => {
    fetchMock.mockReset().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    tokenManager.clear();
  });

  it('ends the session on the server with the current token, then clears it locally', () => {
    tokenManager.store(token);

    apiClient.logout();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/auth\/logout$/);
    expect(init).toMatchObject({ method: 'POST', keepalive: true, headers: { Authorization: `Bearer ${token}` } });
    expect(tokenManager.get()).toBeNull();
  });

  it('still clears the token when the server cannot be reached', async () => {
    fetchMock.mockRejectedValue(new Error('offline'));
    tokenManager.store(token);

    expect(() => apiClient.logout()).not.toThrow();
    await Promise.resolve();

    expect(tokenManager.get()).toBeNull();
  });

  it('does not call the server when nobody is signed in', () => {
    apiClient.logout();

    expect(fetchMock).not.toHaveBeenCalled();
  });
});
