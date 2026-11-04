import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ApiClient } from './api';
import { tokenManager } from './TokenManager';

function response(status: number, body: unknown, contentType: string): Response {
  const res = new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: contentType ? { 'Content-Type': contentType } : {},
  });
  Object.defineProperty(res, 'url', { value: 'http://localhost/api/x' });
  return res;
}

describe('API client - error bodies and ETag polling', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    tokenManager.clear();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('uses the detail of an application/problem+json error as the message', async () => {
    fetchMock.mockResolvedValue(
      response(
        404,
        { type: 'https://readybell.org/problems/not-found', title: 'Not Found', status: 404, detail: 'No endpoint matches this request.' },
        'application/problem+json',
      ),
    );
    await expect(new ApiClient().get('/api/x')).rejects.toMatchObject({
      statusCode: 404,
      message: 'No endpoint matches this request.',
    });
  });

  it('keeps a 404 classified as a normal error, not a network error', async () => {
    fetchMock.mockResolvedValue(response(404, { type: 't', status: 404 }, 'application/problem+json'));
    await expect(new ApiClient().get('/api/x')).rejects.toMatchObject({
      displayError: { type: 'unknown' },
    });
  });

  it('pollOrders returns an empty change set on 304 Not Modified', async () => {
    fetchMock.mockResolvedValue(response(304, null, ''));
    await expect(new ApiClient().pollOrders({ eTag: '"abc"' })).resolves.toEqual({ orders: [], eTag: '"abc"' });
  });
});
