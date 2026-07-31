/**
 * Sidebar Component Tests
 * Verifies Sidebar renders navigation menu and user profile
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@/test/test-utils';
import Sidebar from './Sidebar';

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

  it('renders all navigation items', () => {
    render(<Sidebar />);
    
    // Use getAllByText and check the visible ones (filtering out tooltips)
    const dashboardTexts = screen.getAllByText('Dashboard');
    expect(dashboardTexts.some(el => el.className.includes('md:inline'))).toBeTruthy();
    
    const ordersTexts = screen.getAllByText('Orders');
    expect(ordersTexts.length).toBeGreaterThan(0);
    
    const menuTexts = screen.getAllByText('Menu Management');
    expect(menuTexts.length).toBeGreaterThan(0);
    
    const paymentsTexts = screen.getAllByText('Payments');
    expect(paymentsTexts.length).toBeGreaterThan(0);
    
    const analyticsTexts = screen.getAllByText('Analytics');
    expect(analyticsTexts.length).toBeGreaterThan(0);
    
    const settingsTexts = screen.getAllByText('Settings');
    expect(settingsTexts.length).toBeGreaterThan(0);
    
    const supportTexts = screen.getAllByText('Support');
    expect(supportTexts.length).toBeGreaterThan(0);
  });

  it('renders navigation as links', () => {
    render(<Sidebar />);
    const links = screen.getAllByRole('link');
    expect(links.length).toBeGreaterThanOrEqual(7);
  });

  it('marks navigation items as links to correct routes', () => {
    render(<Sidebar />);
    
    // Find the Dashboard link by looking for the visible span with 'Dashboard' text
    const dashboardLinks = screen.getAllByText('Dashboard');
    const dashboardSpan = dashboardLinks.find(el => el.className.includes('md:inline'));
    const dashboardLink = dashboardSpan?.closest('a');
    expect(dashboardLink?.getAttribute('href')).toBe('/');
    
    // Find the Orders link
    const ordersLinks = screen.getAllByText('Orders');
    const ordersSpan = ordersLinks.find(el => el.className.includes('text-sm'));
    const ordersLink = ordersSpan?.closest('a');
    expect(ordersLink?.getAttribute('href')).toBe('/orders');
  });

  it('calls onNavigate when navigation item is clicked', () => {
    const onNavigate = vi.fn();
    render(<Sidebar onNavigate={onNavigate} />);
    
    // Find the Orders link (the visible one, not tooltip)
    const ordersLinks = screen.getAllByText('Orders');
    const ordersSpan = ordersLinks.find(el => el.className.includes('text-sm'));
    const ordersLink = ordersSpan?.closest('a') as HTMLAnchorElement;
    ordersLink?.click();
    
    expect(onNavigate).toHaveBeenCalledWith('/orders');
  });

  it('renders logout button', () => {
    render(<Sidebar />);
    const logoutButton = screen.getByLabelText('Logout');
    expect(logoutButton).toBeInTheDocument();
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
