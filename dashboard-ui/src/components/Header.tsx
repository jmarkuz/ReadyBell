import { signOut } from '@/utils/session';
import { useCallback, useState, memo } from 'react';
import { useTheme } from '@/context/ThemeContext';
import {
  Menu,
  X,
  Search,
  Bell,
  Moon,
  Sun,
  Volume2,
  VolumeX,
  Settings,
  LogOut,
} from 'lucide-react';
import { restaurantBranding } from '@/config/restaurant';
import { useRestaurant } from '@/context/restaurantContext';
import { useNewOrderAlerts } from '@/context/alertContext';

interface HeaderProps {
  /** The order search only makes sense on the dashboard. */
  showSearch?: boolean;
  onMenuToggle?: () => void;
  onSearch?: (query: string) => void;
  notificationCount?: number;
  onThemeToggle?: () => void;
}

/**
 * Header Component
 * 
 * Navigation bar with:
 * - Logo/home link (left)
 * - Search input with 300ms debouncing (center)
 * - Notification icon with badge count (right)
 * - Theme toggle button (right, before settings)
 * - Settings menu / profile dropdown (right)
 * 
 * Responsive:
 * - Desktop: All elements visible
 * - Tablet: Compact spacing, hide some text labels
 * - Mobile: Search in dropdown, compact icons
 */
export default memo(function Header({
  showSearch = true,
  onMenuToggle,
  onSearch,
  notificationCount = 0,
  onThemeToggle,
}: HeaderProps) {
  const { currentMode, toggleTheme } = useTheme();
  const { soundEnabled, setSoundEnabled } = useNewOrderAlerts();
  const [searchQuery, setSearchQuery] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [debounceTimer, setDebounceTimer] = useState<NodeJS.Timeout | null>(null);

  // Debounced search handler (300ms delay)
  const handleSearchChange = useCallback(
    (value: string) => {
      setSearchQuery(value);

      // Clear existing timer
      if (debounceTimer) {
        clearTimeout(debounceTimer);
      }

      // Set new debounce timer
      const timer = setTimeout(() => {
        onSearch?.(value);
      }, 300);

      setDebounceTimer(timer);
    },
    [debounceTimer, onSearch]
  );

  // Handle theme toggle
  const restaurant = useRestaurant();
  const handleThemeToggle = useCallback(() => {
    toggleTheme();
    onThemeToggle?.();
  }, [toggleTheme, onThemeToggle]);

  // Clear search
  const handleClearSearch = useCallback(() => {
    setSearchQuery('');
    onSearch?.('');
  }, [onSearch]);

  return (
    <nav className="flex items-center justify-between h-16 px-4 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800">
      {/* Left: Menu toggle (mobile only) + Logo */}
      <div className="flex items-center gap-3">
        {/* Menu toggle - visible on mobile/tablet */}
        <button
          onClick={onMenuToggle}
          className="lg:hidden p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
          aria-label="Toggle navigation menu"
          aria-controls="main-navigation"
        >
          <Menu className="w-5 h-5" aria-hidden="true" />
        </button>

        {/* Restaurant brand - Home link */}
        <a
          href="/"
          className="flex items-center gap-3 hover:opacity-80 transition-opacity"
          aria-label={`${restaurant.name} dashboard home`}
        >
          <div className="w-9 h-9 bg-stone-800 dark:bg-stone-100 text-white dark:text-stone-900 rounded-full flex items-center justify-center">
            <restaurantBranding.Logo className="w-5 h-5" aria-hidden="true" />
          </div>
          <span
            className="text-2xl hidden sm:inline"
            style={{ fontFamily: 'var(--font-display)', fontWeight: 300, letterSpacing: '-0.02em' }}
          >
            {restaurant.name}
          </span>
        </a>
      </div>

      {/* Center: Search input (desktop and tablet) */}
      {showSearch ? (
      <div className="hidden md:flex flex-1 max-w-md mx-4">
        <div className="relative w-full">
          <label htmlFor="search-orders" className="sr-only">
            Search orders by ID, customer name, or items
          </label>
          <input
            id="search-orders"
            type="text"
            placeholder="Search orders by ID, customer, items..."
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="w-full px-4 py-2 pl-10 bg-slate-100 dark:bg-slate-800 border border-transparent rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 text-sm transition-colors"
            aria-label="Search orders"
            aria-describedby="search-help"
          />
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-slate-400" aria-hidden="true" />
          {searchQuery && (
            <button
              onClick={handleClearSearch}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 p-1 focus:outline-none focus:ring-2 focus:ring-blue-500 rounded-full"
              aria-label="Clear search"
              type="button"
            >
              <X className="w-4 h-4" aria-hidden="true" />
            </button>
          )}
        </div>
        <span id="search-help" className="sr-only">Debounced search, results update automatically</span>
      </div>
      ) : (
        <div className="flex-1" />
      )}

      {/* Right: Notifications, Theme Toggle, Settings */}
      <div className="flex items-center gap-2 md:gap-4" role="toolbar" aria-label="Header actions">
        {/* Search toggle (mobile only) */}
        <button
          onClick={() => setSearchOpen(!searchOpen)}
          className="md:hidden p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
          aria-label="Toggle search"
          aria-expanded={searchOpen}
          type="button"
        >
          <Search className="w-5 h-5" aria-hidden="true" />
        </button>

        {/* Notification icon with badge */}
        <button
          className="relative p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
          aria-label={`Notifications ${notificationCount > 0 ? `${notificationCount} unread` : 'none'}`}
          type="button"
        >
          <Bell className="w-5 h-5" aria-hidden="true" />
          {notificationCount > 0 && (
            <span 
              className="absolute top-1 right-1 w-4 h-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold"
              role="status"
              aria-label={`${notificationCount} new notifications`}
            >
              {notificationCount > 99 ? '99+' : notificationCount}
            </span>
          )}
        </button>

        {/* New-order sound and vibration: off until staff turn it on (browsers need that click anyway) */}
        <button
          onClick={() => setSoundEnabled(!soundEnabled)}
          className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
          aria-label="New order sound"
          aria-pressed={soundEnabled}
          title={soundEnabled ? 'New order sound is on' : 'Play a sound when a new order arrives'}
          type="button"
        >
          {soundEnabled ? (
            <Volume2 className="w-5 h-5 text-slate-900 dark:text-slate-100" aria-hidden="true" />
          ) : (
            <VolumeX className="w-5 h-5 text-slate-500" aria-hidden="true" />
          )}
        </button>

        {/* Theme toggle button */}
        <button
          onClick={handleThemeToggle}
          className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
          aria-label={`Switch to ${currentMode === 'dark' ? 'light' : 'dark'} theme`}
          type="button"
        >
          {currentMode === 'dark' ? (
            <Sun className="w-5 h-5 text-yellow-500" aria-hidden="true" />
          ) : (
            <Moon className="w-5 h-5 text-slate-600" aria-hidden="true" />
          )}
        </button>

        {/* Settings menu button */}
        <div className="relative">
          <button
            onClick={() => setSettingsOpen(!settingsOpen)}
            className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
            aria-label="Open settings menu"
            aria-expanded={settingsOpen}
            aria-haspopup="menu"
            type="button"
          >
            <Settings className="w-5 h-5" aria-hidden="true" />
          </button>

          {/* Settings dropdown menu */}
          {settingsOpen && (
            <div 
              className="absolute right-0 mt-2 w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg shadow-lg z-50"
              role="menu"
              aria-label="Settings"
            >
              <button
                className="w-full text-left px-4 py-2 hover:bg-slate-100 dark:hover:bg-slate-800 text-sm flex items-center gap-2 border-b border-slate-200 dark:border-slate-800"
                onClick={() => setSettingsOpen(false)}
                role="menuitem"
                type="button"
              >
                <Settings className="w-4 h-4" aria-hidden="true" />
                Settings
              </button>
              <button
                className="w-full text-left px-4 py-2 hover:bg-slate-100 dark:hover:bg-slate-800 text-sm flex items-center gap-2 text-red-600 dark:text-red-400"
                onClick={() => {
                  setSettingsOpen(false);
                  signOut();
                }}
                role="menuitem"
                type="button"
              >
                <LogOut className="w-4 h-4" aria-hidden="true" />
                Logout
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Mobile search dropdown */}
      {searchOpen && (
        <div 
          className="absolute top-16 left-0 right-0 md:hidden bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 p-4"
          role="search"
        >
          <label htmlFor="search-orders-mobile" className="sr-only">
            Search orders
          </label>
          <input
            id="search-orders-mobile"
            type="text"
            placeholder="Search orders..."
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="w-full px-4 py-2 bg-slate-100 dark:bg-slate-800 border border-transparent rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
            autoFocus
          />
        </div>
      )}
    </nav>
  );
});
