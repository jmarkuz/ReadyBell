import { describe, it, expect, vi, afterEach } from 'vitest';
import { signOut } from './session';
import { apiClient } from './api';

describe('signOut', () => {
  const original = window.location;
  afterEach(() => {
    Object.defineProperty(window, 'location', { value: original, writable: true });
    vi.restoreAllMocks();
  });

  it('clears the session and goes to the login page', () => {
    const logout = vi.spyOn(apiClient, 'logout').mockImplementation(() => {});
    Object.defineProperty(window, 'location', { value: { href: '/' }, writable: true });

    signOut();

    expect(logout).toHaveBeenCalledTimes(1);
    expect(window.location.href).toBe('/login');
  });
});
