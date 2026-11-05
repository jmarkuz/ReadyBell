import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ApiClient, ApiError, normalizeOrderRow, fromBackendStatus, toBackendStatus } from './api';
import { tokenManager } from './TokenManager';

function jsonResponse(status: number, body: unknown, url: string): Response {
  const res = new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
  Object.defineProperty(res, 'url', { value: `http://localhost${url}` });
  return res;
}

describe('API client - auth endpoints and backend mapping', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    tokenManager.clear();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    tokenManager.clear();
  });

  it('a 401 from /api/login is returned to the caller without refresh, logout or redirect', async () => {
    const hrefBefore = window.location.href;
    fetchMock.mockImplementation(async () =>
      jsonResponse(401, { type: 'urn:readybell:auth:invalid-credentials', title: 'Invalid Credentials', status: 401, detail: 'Invalid username or password' }, '/api/login'),
    );

    const client = new ApiClient();
    await expect(client.login({ username: 'staff', password: 'nope' })).rejects.toMatchObject({ statusCode: 401 });
    await expect(client.login({ username: 'staff', password: 'nope' })).rejects.toBeInstanceOf(ApiError);

    // one fetch per login attempt: no refresh call was made
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls.every(([url]) => String(url).endsWith('/api/login'))).toBe(true);
    expect(window.location.href).toBe(hrefBefore);
  });

  it('login stores the token where the request interceptor reads it', async () => {
    // header.payload.signature with the claims TokenManager.validate() requires
    const payload = btoa(JSON.stringify({ sub: 'staff', role: 'STAFF', tenant_id: 't-1', exp: Math.floor(Date.now() / 1000) + 3600 }))
      .replace(/=+$/, '');
    const token = `eyJhbGciOiJIUzI1NiJ9.${payload}.sig`;
    fetchMock.mockImplementation(async () => jsonResponse(200, { token, role: 'STAFF', tenantId: 't-1', username: 'staff', expiresIn: 3600 }, '/api/login'));

    await new ApiClient().login({ username: 'staff', password: 'password' });

    expect(tokenManager.get()).toBe(token);
    expect(tokenManager.validate()).toBe(true);
  });

  it('getOrders converts dates and maps IN_PROGRESS to PREPARING', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, {
      items: [
        { id: '1', orderId: '#101', customerName: 'Anna', itemsOrdered: '2x Burger', deliveryETA: '2026-01-01T10:00:00Z', status: 'IN_PROGRESS', estimatedWaitTime: 5, itemCount: 2 },
        { id: '2', orderId: '#102', customerName: null, itemsOrdered: '', deliveryETA: null, status: 'CREATED', estimatedWaitTime: null, itemCount: 0 },
      ],
      totalCount: 2, pageCount: 1, currentPage: 1,
    }, '/api/orders'));

    const page = await new ApiClient().getOrders({ pageSize: 100 });

    expect(page.items[0].status).toBe('PREPARING');
    expect(page.items[0].deliveryETA).toEqual(new Date('2026-01-01T10:00:00Z'));
    expect(page.items[1].deliveryETA).toBeNull();
    expect(page.items[1].status).toBe('CREATED');
  });

  it('after a token refresh the original request is replayed with its method and body', async () => {
    const fakeJwt = (sub: string) => {
      const payload = btoa(JSON.stringify({ sub, role: 'STAFF', tenant_id: 't-1', exp: Math.floor(Date.now() / 1000) + 3600 }))
        .replace(/=+$/, '');
      return `eyJhbGciOiJIUzI1NiJ9.${payload}.sig`;
    };
    const oldToken = fakeJwt('old');
    const newToken = fakeJwt('new');
    tokenManager.store(oldToken);

    const calls: Array<{ url: string; init?: RequestInit }> = [];
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      if (url.endsWith('/api/auth/refresh')) return jsonResponse(200, { token: newToken }, '/api/auth/refresh');
      const auth = (init?.headers as Record<string, string>).Authorization;
      return auth === `Bearer ${newToken}`
        ? jsonResponse(200, { id: 'o1', status: 'IN_PROGRESS', updatedAt: 'now' }, '/api/orders/o1/status')
        : jsonResponse(401, { title: 'Unauthorized' }, '/api/orders/o1/status');
    });

    const result = await new ApiClient().updateOrderStatus('o1', 'IN_PROGRESS');

    expect(result.status).toBe('IN_PROGRESS');
    const retry = calls[calls.length - 1];
    expect(retry.url).toMatch(/\/api\/orders\/o1\/status$/);
    expect(retry.init?.method).toBe('PATCH');                       // was always GET before
    expect(JSON.parse(String(retry.init?.body))).toEqual({ status: 'IN_PROGRESS' });
    expect((retry.init?.headers as Record<string, string>).Authorization).toBe(`Bearer ${newToken}`);
  });

  it('status names map both ways and leave other statuses alone', () => {
    expect(fromBackendStatus('IN_PROGRESS')).toBe('PREPARING');
    expect(fromBackendStatus('READY')).toBe('READY');
    expect(toBackendStatus('PREPARING')).toBe('IN_PROGRESS');
    expect(toBackendStatus('CANCELLED')).toBe('CANCELLED');
    expect(normalizeOrderRow({ id: 'x', orderId: '#X', itemsOrdered: '', itemCount: 0, estimatedWaitTime: null, deliveryETA: null, status: 'IN_PROGRESS' }).status).toBe('PREPARING');
  });
});
