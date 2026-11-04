/**
 * Header Component Tests
 * Verifies Header renders with search, notifications, theme toggle, and settings
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@/test/test-utils';
import Header from './Header';
import { signOut } from '@/utils/session';

import { restaurantBranding } from '@/config/restaurant';

vi.mock('@/utils/session', () => ({ signOut: vi.fn() }));

describe('Header Component', () => {
  it('renders header without errors', () => {
    render(<Header />);
    expect(screen.getByText(restaurantBranding.name)).toBeInTheDocument();
  });

  it('renders the restaurant brand link', () => {
    render(<Header />);
    expect(
      screen.getByRole('link', { name: `${restaurantBranding.name} dashboard home` })
    ).toBeInTheDocument();
  });

  it('renders search input on desktop', () => {
    render(<Header />);
    const searchInput = screen.getByPlaceholderText(/search orders/i);
    expect(searchInput).toBeInTheDocument();
  });

  it('debounces search input', async () => {
    const onSearch = vi.fn();
    render(<Header onSearch={onSearch} />);
    
    const searchInput = screen.getByPlaceholderText(/search orders/i) as HTMLInputElement;
    
    // Type search query
    fireEvent.change(searchInput, { target: { value: 'test' } });
    
    // Callback should not be called immediately
    expect(onSearch).not.toHaveBeenCalled();
    
    // Wait for debounce (300ms)
    await waitFor(() => {
      expect(onSearch).toHaveBeenCalledWith('test');
    }, { timeout: 500 });
  });

  it('clears search when clear button is clicked', async () => {
    const onSearch = vi.fn();
    render(<Header onSearch={onSearch} />);
    
    const searchInput = screen.getByPlaceholderText(/search orders/i) as HTMLInputElement;
    
    // Type something
    fireEvent.change(searchInput, { target: { value: 'test query' } });
    
    // Wait for the clear button to appear
    await waitFor(() => {
      const clearButton = screen.getByLabelText('Clear search');
      fireEvent.click(clearButton);
    }, { timeout: 500 });
    
    // Input should be cleared
    expect(searchInput.value).toBe('');
  });

  it('displays notification badge', () => {
    render(<Header notificationCount={5} />);
    const badge = screen.getByText('5');
    expect(badge).toBeInTheDocument();
  });

  it('hides notification badge when count is 0', () => {
    render(<Header notificationCount={0} />);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('renders theme toggle button', () => {
    render(<Header />);
    const themeToggle = screen.getByLabelText(/switch to (light|dark) theme/i);
    expect(themeToggle).toBeInTheDocument();
  });

  it('calls onThemeToggle when theme button is clicked', () => {
    const onThemeToggle = vi.fn();
    render(<Header onThemeToggle={onThemeToggle} />);
    
    const themeToggle = screen.getByLabelText(/switch to (light|dark) theme/i);
    fireEvent.click(themeToggle);
    
    expect(onThemeToggle).toHaveBeenCalled();
  });

  it('renders settings menu button', () => {
    render(<Header />);
    const settingsButton = screen.getByLabelText('Open settings menu');
    expect(settingsButton).toBeInTheDocument();
  });

  it('signs out from the settings menu and closes it', async () => {
    render(<Header />);
    fireEvent.click(screen.getByLabelText('Open settings menu'));
    fireEvent.click(await screen.findByRole('menuitem', { name: /logout/i }));
    expect(signOut).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
  });

  it('opens and closes settings menu', async () => {
    render(<Header />);
    const settingsButton = screen.getByLabelText('Open settings menu');
    
    // Open menu
    fireEvent.click(settingsButton);
    
    // Check menu is open
    const settingsLink = await screen.findByText('Settings');
    expect(settingsLink).toBeInTheDocument();
    
    // Close menu
    fireEvent.click(settingsButton);
  });

  it('renders menu toggle for mobile', () => {
    render(<Header />);
    const menuToggle = screen.getByLabelText('Toggle navigation menu');
    expect(menuToggle).toBeInTheDocument();
  });

  it('calls onMenuToggle when menu button is clicked', () => {
    const onMenuToggle = vi.fn();
    render(<Header onMenuToggle={onMenuToggle} />);
    
    const menuToggle = screen.getByLabelText('Toggle navigation menu');
    fireEvent.click(menuToggle);
    
    expect(onMenuToggle).toHaveBeenCalled();
  });
});
