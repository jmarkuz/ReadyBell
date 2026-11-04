import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Staff from './Staff';
import Settings from './Settings';
import Restaurants from './Restaurants';
import RequireRole from '@/components/RequireRole';
import { RestaurantProvider } from '@/context/RestaurantProvider';
import { useRestaurant } from '@/context/restaurantContext';
import { apiClient } from '@/utils/api';

vi.mock('@/utils/api', async (orig) => ({
  ...(await orig<typeof import('@/utils/api')>()),
  apiClient: {
    listStaff: vi.fn(), createStaffAccount: vi.fn(), deactivateStaffAccount: vi.fn(),
    listRestaurants: vi.fn(), createRestaurant: vi.fn(), deactivateRestaurant: vi.fn(),
    updateRestaurantConfig: vi.fn(), getRestaurantProfile: vi.fn(), getUserProfile: vi.fn(),
  },
}));

const staff = [
  { accountId: 'a1', username: 'boss', role: 'RESTAURANT_ADMIN' as const, status: 'ACTIVE' as const },
  { accountId: 'a2', username: 'anna', role: 'STAFF' as const, status: 'ACTIVE' as const },
  { accountId: 'a3', username: 'old', role: 'STAFF' as const, status: 'DEACTIVATED' as const },
];

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
});

describe('Staff page', () => {
  it('lists accounts and only offers deactivation for active ones', async () => {
    vi.mocked(apiClient.listStaff).mockResolvedValue(staff);
    render(<Staff />);
    expect(await screen.findByText('anna')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deactivate anna' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Deactivate old' })).not.toBeInTheDocument();
  });

  it('creates an account and reloads the list', async () => {
    vi.mocked(apiClient.listStaff).mockResolvedValue(staff);
    vi.mocked(apiClient.createStaffAccount).mockResolvedValue({ accountId: 'n', username: 'new', role: 'STAFF', status: 'ACTIVE' });
    render(<Staff />);
    await screen.findByText('anna');
    await userEvent.type(screen.getByLabelText('Username'), ' newbie ');
    await userEvent.type(screen.getByLabelText(/Password/), 'long-enough-1');
    await userEvent.click(screen.getByRole('button', { name: 'Add account' }));
    await waitFor(() => expect(apiClient.createStaffAccount).toHaveBeenCalledWith({ username: 'newbie', password: 'long-enough-1', role: 'STAFF' }));
    expect(vi.mocked(apiClient.listStaff).mock.calls.length).toBeGreaterThan(1);
  });

  it('shows the server error, e.g. a duplicate username', async () => {
    vi.mocked(apiClient.listStaff).mockResolvedValue(staff);
    vi.mocked(apiClient.createStaffAccount).mockRejectedValue(new Error('Username already taken'));
    render(<Staff />);
    await screen.findByText('anna');
    await userEvent.type(screen.getByLabelText('Username'), 'anna');
    await userEvent.type(screen.getByLabelText(/Password/), 'long-enough-1');
    await userEvent.click(screen.getByRole('button', { name: 'Add account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Username already taken');
  });

  it('asks before deactivating', async () => {
    vi.mocked(apiClient.listStaff).mockResolvedValue(staff);
    vi.mocked(apiClient.deactivateStaffAccount).mockResolvedValue(staff[1]);
    vi.mocked(window.confirm).mockReturnValueOnce(false);
    render(<Staff />);
    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate anna' }));
    expect(apiClient.deactivateStaffAccount).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Deactivate anna' }));
    await waitFor(() => expect(apiClient.deactivateStaffAccount).toHaveBeenCalledWith('a2'));
  });
});

describe('Settings page', () => {
  it('saves the restaurant name and refreshes the branding', async () => {
    vi.mocked(apiClient.getUserProfile).mockReturnValue({ role: 'RESTAURANT_ADMIN' } as never);
    vi.mocked(apiClient.getRestaurantProfile).mockResolvedValue({ name: 'Café Aurora', identifier: 'aurora' });
    vi.mocked(apiClient.updateRestaurantConfig).mockResolvedValue({ name: 'Aurora 2' });
    render(<RestaurantProvider><Settings /></RestaurantProvider>);
    const input = await screen.findByLabelText('Name shown to customers');
    await waitFor(() => expect(input).toHaveValue('Café Aurora'));
    expect(screen.getByText('aurora')).toBeInTheDocument();
    await userEvent.clear(input);
    await userEvent.type(input, 'Aurora 2');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(apiClient.updateRestaurantConfig).toHaveBeenCalledWith({ name: 'Aurora 2' }));
    expect(await screen.findByText('Saved.')).toBeInTheDocument();
    expect(vi.mocked(apiClient.getRestaurantProfile).mock.calls.length).toBeGreaterThan(1);
  });
});

describe('Restaurants page', () => {
  it('lists restaurants and creates one with its first admin', async () => {
    vi.mocked(apiClient.listRestaurants).mockResolvedValue([
      { tenantId: 't1', identifier: 'aurora', name: 'Café Aurora', status: 'ACTIVE', adminUsername: null },
    ]);
    vi.mocked(apiClient.createRestaurant).mockResolvedValue({ tenantId: 't2', identifier: 'bistro', name: 'Bistro', status: 'ACTIVE', adminUsername: 'bob' });
    render(<Restaurants />);
    expect(await screen.findByText('Café Aurora')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Name'), 'Bistro');
    await userEvent.type(screen.getByLabelText(/Identifier/), 'bistro');
    await userEvent.type(screen.getByLabelText(/First admin username/), 'bob');
    await userEvent.type(screen.getByLabelText(/Admin password/), 'long-enough-1');
    await userEvent.click(screen.getByRole('button', { name: 'Add restaurant' }));
    await waitFor(() => expect(apiClient.createRestaurant).toHaveBeenCalledWith({
      identifier: 'bistro', name: 'Bistro', adminUsername: 'bob', adminPassword: 'long-enough-1',
    }));
  });

  it('creates a restaurant without an admin when no username is given', async () => {
    vi.mocked(apiClient.listRestaurants).mockResolvedValue([]);
    vi.mocked(apiClient.createRestaurant).mockResolvedValue({ tenantId: 't', identifier: 'x', name: 'X', status: 'ACTIVE', adminUsername: null });
    render(<Restaurants />);
    expect(await screen.findByText(/No restaurants yet/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Name'), 'X');
    await userEvent.type(screen.getByLabelText(/Identifier/), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Add restaurant' }));
    await waitFor(() => expect(apiClient.createRestaurant).toHaveBeenCalledWith({
      identifier: 'x', name: 'X', adminUsername: undefined, adminPassword: undefined,
    }));
  });

  it('deactivates after confirmation', async () => {
    vi.mocked(apiClient.listRestaurants).mockResolvedValue([
      { tenantId: 't1', identifier: 'aurora', name: 'Café Aurora', status: 'ACTIVE', adminUsername: null },
    ]);
    vi.mocked(apiClient.deactivateRestaurant).mockResolvedValue({ tenantId: 't1', identifier: 'aurora', name: 'Café Aurora', status: 'DEACTIVATED', adminUsername: null });
    render(<Restaurants />);
    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate Café Aurora' }));
    await waitFor(() => expect(apiClient.deactivateRestaurant).toHaveBeenCalledWith('t1'));
  });
});

describe('RequireRole', () => {
  const renderAt = (role: string | null) => {
    vi.mocked(apiClient.getUserProfile).mockReturnValue(role ? ({ role } as never) : null);
    return render(
      <MemoryRouter initialEntries={['/staff']}>
        <Routes>
          <Route path="/staff" element={<RequireRole roles={['RESTAURANT_ADMIN']}><p>admin page</p></RequireRole>} />
          <Route path="/" element={<p>home</p>} />
          <Route path="/restaurants" element={<p>platform home</p>} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('lets the allowed role in', () => {
    renderAt('RESTAURANT_ADMIN');
    expect(screen.getByText('admin page')).toBeInTheDocument();
  });
  it('sends staff to the dashboard', () => {
    renderAt('STAFF');
    expect(screen.getByText('home')).toBeInTheDocument();
  });
  it('sends the platform admin to the restaurants page', () => {
    renderAt('PLATFORM_ADMIN');
    expect(screen.getByText('platform home')).toBeInTheDocument();
  });
});

describe('RestaurantProvider', () => {
  function Name() { return <p>{useRestaurant().name}</p>; }

  it('falls back to the default brand when the profile cannot be loaded', async () => {
    vi.mocked(apiClient.getUserProfile).mockReturnValue({ role: 'STAFF' } as never);
    vi.mocked(apiClient.getRestaurantProfile).mockRejectedValue(new Error('404'));
    render(<RestaurantProvider><Name /></RestaurantProvider>);
    await waitFor(() => expect(apiClient.getRestaurantProfile).toHaveBeenCalled());
    expect(screen.getByText('ReadyBell')).toBeInTheDocument();
  });

  it('does not look up a restaurant for the platform admin', async () => {
    vi.mocked(apiClient.getUserProfile).mockReturnValue({ role: 'PLATFORM_ADMIN' } as never);
    render(<RestaurantProvider><Name /></RestaurantProvider>);
    await new Promise((r) => setTimeout(r, 20));
    expect(apiClient.getRestaurantProfile).not.toHaveBeenCalled();
  });
});
