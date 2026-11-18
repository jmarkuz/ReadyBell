import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Login from './Login';
import { apiClient, ApiError } from '@/utils/api';

vi.mock('@/utils/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/utils/api')>();
  return { ...original, apiClient: { login: vi.fn() } };
});

const apiError = (status: number) =>
  new ApiError('x', status, { title: 't', message: 'm', canRetry: false } as never);

async function submit(user: ReturnType<typeof userEvent.setup>, name: string, pass: string, restaurant = '') {
  if (restaurant) await user.type(screen.getByLabelText('Restaurant'), restaurant);
  await user.type(screen.getByLabelText('Username'), name);
  await user.type(screen.getByLabelText('Password'), pass);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('Login page', () => {
  beforeEach(() => {
    vi.mocked(apiClient.login).mockReset();
    window.localStorage.clear();
  });

  it('signs in to the named restaurant (identifier trimmed) and remembers it for next time', async () => {
    vi.mocked(apiClient.login).mockResolvedValue({ token: 't', role: 'STAFF', tenantId: 'x', expiresIn: 3600 });
    const user = userEvent.setup();
    const { unmount } = render(<Login />);
    await submit(user, 'admin', 'pw', ' aurora ');
    await waitFor(() => expect(apiClient.login).toHaveBeenCalledWith({ restaurant: 'aurora', username: 'admin', password: 'pw' }));
    unmount();

    render(<Login />);
    expect(screen.getByLabelText('Restaurant')).toHaveValue('aurora');
  });

  it('sends no restaurant for the platform operator', async () => {
    vi.mocked(apiClient.login).mockResolvedValue({ token: 't', role: 'PLATFORM_ADMIN', tenantId: '', expiresIn: 3600 });
    const user = userEvent.setup();
    render(<Login />);
    await submit(user, 'platform', 'pw');
    await waitFor(() => expect(apiClient.login).toHaveBeenCalledWith({ username: 'platform', password: 'pw' }));
    expect(vi.mocked(apiClient.login).mock.calls[0][0]).not.toHaveProperty('restaurant');
  });

  it('signs in with the typed credentials (username trimmed)', async () => {
    vi.mocked(apiClient.login).mockResolvedValue({ token: 't', role: 'STAFF', tenantId: 'x', expiresIn: 3600 });
    const user = userEvent.setup();
    render(<Login />);
    await submit(user, ' staff ', 'password');
    await waitFor(() => expect(apiClient.login).toHaveBeenCalledWith({ username: 'staff', password: 'password' }));
  });

  it('shows a clear message for wrong credentials and keeps the form usable', async () => {
    vi.mocked(apiClient.login).mockRejectedValue(apiError(401));
    const user = userEvent.setup();
    render(<Login />);
    await submit(user, 'staff', 'nope');
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid restaurant, username or password.');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  it('tells the user to wait when throttled (429)', async () => {
    vi.mocked(apiClient.login).mockRejectedValue(apiError(429));
    const user = userEvent.setup();
    render(<Login />);
    await submit(user, 'staff', 'nope');
    expect(await screen.findByRole('alert')).toHaveTextContent(/too many failed attempts/i);
  });

  it('shows a generic message for network failures', async () => {
    vi.mocked(apiClient.login).mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    render(<Login />);
    await submit(user, 'staff', 'x');
    expect(await screen.findByRole('alert')).toHaveTextContent(/sign-in failed/i);
  });

  it('requires both fields', async () => {
    const user = userEvent.setup();
    render(<Login />);
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(apiClient.login).not.toHaveBeenCalled();
  });
});
