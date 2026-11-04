/**
 * Sidebar Component Tests
 * Verifies Sidebar renders navigation menu and user profile
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@/test/test-utils';
import Sidebar from './Sidebar';
import { signOut } from '@/utils/session';

vi.mock('@/utils/session', () => ({ signOut: vi.fn() }));

describe('Sidebar Component', () => {
  it('renders sidebar without errors', () => {
    const { container } = render(<Sidebar />);
    expect(container).toBeTruthy();
  });

  it('renders ReadyBell logo', () => {
    render(<Sidebar />);
    expect(screen.getByText('RB')).toBeInTheDocument();
  });

  it('renders navigation menu', () => {
    render(<Sidebar />);
    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    expect(nav).toBeInTheDocument();
  });

  it('offers only the dashboard to staff', () => {
    render(<Sidebar role="STAFF" />);
    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    expect(within(nav).getAllByRole('link').map((l) => l.getAttribute('href'))).toEqual(['/']);
  });

  it('offers the dashboard, staff and settings to a restaurant admin', () => {
    render(<Sidebar role="RESTAURANT_ADMIN" />);
    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    expect(within(nav).getAllByRole('link').map((l) => l.getAttribute('href'))).toEqual(['/', '/staff', '/settings']);
  });

  it('offers only restaurants to the platform admin', () => {
    render(<Sidebar role="PLATFORM_ADMIN" />);
    const nav = screen.getByRole('navigation', { name: /main navigation/i });
    expect(within(nav).getAllByRole('link').map((l) => l.getAttribute('href'))).toEqual(['/restaurants']);
  });

  it('no longer lists the unfinished sections', () => {
    render(<Sidebar role="RESTAURANT_ADMIN" />);
    for (const label of ['Menu Management', 'Payments', 'Analytics', 'Support']) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });

  it('calls onNavigate when a navigation item is clicked', () => {
    const onNavigate = vi.fn();
    render(<Sidebar role="RESTAURANT_ADMIN" onNavigate={onNavigate} />);
    const staff = screen.getAllByText('Staff').find((el) => el.className.includes('text-sm'));
    (staff?.closest('a') as HTMLAnchorElement).click();
    expect(onNavigate).toHaveBeenCalledWith('/staff');
  });

  it('renders logout button', () => {
    render(<Sidebar />);
    const logoutButton = screen.getByLabelText('Logout from dashboard');
    expect(logoutButton).toBeInTheDocument();
  });

  it('signs out when the logout button is clicked', () => {
    render(<Sidebar />);
    screen.getByLabelText('Logout from dashboard').click();
    expect(signOut).toHaveBeenCalledTimes(1);
  });

  it('renders user profile section when profile provided', () => {
    const userProfile = {
      name: 'John Doe',
      email: 'john@example.com',
      role: 'STAFF',
      restaurantName: 'Test Restaurant',
    };
    
    render(<Sidebar userProfile={userProfile} />);
    
    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('Test Restaurant')).toBeInTheDocument();
    expect(screen.getByText('STAFF')).toBeInTheDocument();
  });

  it('renders user avatar initials when no avatar URL', () => {
    const userProfile = {
      name: 'John Doe',
      email: 'john@example.com',
      role: 'STAFF',
      restaurantName: 'Test Restaurant',
    };
    
    render(<Sidebar userProfile={userProfile} />);
    
    expect(screen.getByText('JD')).toBeInTheDocument();
  });

  it('renders profile loading skeleton when no profile', () => {
    const { container } = render(<Sidebar />);
    
    const skeleton = container.querySelector('[class*="animate-pulse"]');
    expect(skeleton).toBeInTheDocument();
  });
});
