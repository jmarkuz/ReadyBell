import { useCallback, useState } from 'react';
import { useTheme } from '@/context/ThemeContext';
import {
  Menu,
  X,
  Search,
  Bell,
  Moon,
  Sun,
  Settings,
  LogOut,
} from 'lucide-react';

interface HeaderProps {
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
export default function Header({
  onMenuToggle,
  onSearch,
  notificationCount = 0,
  onThemeToggle,
}: HeaderProps) {
  const { currentMode, toggleTheme } = useTheme();
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
    <header className="flex items-center justify-between h-16 px-4 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800">
      {/* Left: Menu toggle (mobile only) + Logo */}
      <div className="flex items-center gap-3">
        {/* Menu toggle - visible on mobile/tablet */}
        <button
          onClick={onMenuToggle}
          className="lg:hidden p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
          aria-label="Toggle menu"
        >
          <Menu className="w-5 h-5" />
        </button>

        {/* Logo */}
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-blue-600 dark:bg-blue-500 rounded-lg flex items-center justify-center">
            <span className="text-white font-bold text-sm">RB</span>
          </div>
          <span className="text-lg font-bold hidden sm:inline">ReadyBell</span>
        </div>
      </div>

      {/* Center: Search input (desktop and tablet) */}
      <div className="hidden md:flex flex-1 max-w-md mx-4">
        <div className="relative w-full">
          <input
            type="text"
            placeholder="Search orders by ID, customer, items..."
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="w-full px-4 py-2 pl-10 bg-slate-100 dark:bg-slate-800 border border-transparent rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 text-sm transition-colors"
            aria-label="Search orders"
          />
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-slate-400" />
          {searchQuery && (
            <button
              onClick={handleClearSearch}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 p-1"
              aria-label="Clear search"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Right: Notifications, Theme Toggle, Settings */}
      <div className="flex items-center gap-2 md:gap-4">
        {/* Search toggle (mobile only) */}
        <button
          onClick={() => setSearchOpen(!searchOpen)}
          className="md:hidden p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
          aria-label="Toggle search"
        >
          <Search className="w-5 h-5" />
        </button>

        {/* Notification icon with badge */}
        <button
          className="relative p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
          aria-label={`Notifications - ${notificationCount} unread`}
        >
          <Bell className="w-5 h-5" />
          {notificationCount > 0 && (
            <span className="absolute top-1 right-1 w-4 h-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold">
              {notificationCount > 99 ? '99+' : notificationCount}
            </span>
          )}
        </button>

        {/* Theme toggle button */}
        <button
          onClick={handleThemeToggle}
          className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
          aria-label={`Toggle light/dark theme (currently ${currentMode})`}
        >
          {currentMode === 'dark' ? (
            <Sun className="w-5 h-5 text-yellow-500" />
          ) : (
            <Moon className="w-5 h-5 text-slate-600" />
          )}
        </button>

        {/* Settings menu button */}
        <div className="relative">
          <button
            onClick={() => setSettingsOpen(!settingsOpen)}
            className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
            aria-label="Settings menu"
            aria-expanded={settingsOpen}
          >
            <Settings className="w-5 h-5" />
          </button>

          {/* Settings dropdown menu */}
          {settingsOpen && (
            <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg shadow-lg z-50">
              <button
                className="w-full text-left px-4 py-2 hover:bg-slate-100 dark:hover:bg-slate-800 text-sm flex items-center gap-2 border-b border-slate-200 dark:border-slate-800"
                onClick={() => setSettingsOpen(false)}
              >
                <Settings className="w-4 h-4" />
                Settings
              </button>
              <button
                className="w-full text-left px-4 py-2 hover:bg-slate-100 dark:hover:bg-slate-800 text-sm flex items-center gap-2 text-red-600 dark:text-red-400"
                onClick={() => {
                  setSettingsOpen(false);
                  // Logout logic will be added later
                }}
              >
                <LogOut className="w-4 h-4" />
                Logout
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Mobile search dropdown */}
      {searchOpen && (
        <div className="absolute top-16 left-0 right-0 md:hidden bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 p-4">
          <input
            type="text"
            placeholder="Search orders..."
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            className="w-full px-4 py-2 bg-slate-100 dark:bg-slate-800 border border-transparent rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
            autoFocus
            aria-label="Search orders (mobile)"
          />
        </div>
      )}
    </header>
  );
}
