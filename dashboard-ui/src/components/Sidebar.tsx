import { signOut } from '@/utils/session';
import React, { memo } from 'react';
import { NavLink } from 'react-router-dom';
import {
  Home,
  ShoppingCart,
  Utensils,
  CreditCard,
  BarChart3,
  Settings,
  HelpCircle,
  LogOut,
} from 'lucide-react';

interface SidebarProps {
  onNavigate?: (route: string) => void;
  isCollapsed?: boolean;
  onToggleCollapse?: () => void;
  userProfile?: {
    name: string;
    email: string;
    role: string;
    avatarUrl?: string;
    restaurantName: string;
  };
}

interface MenuItem {
  id: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  route: string;
}

/**
 * Sidebar Navigation Component
 * 
 * Left sidebar with:
 * - ReadyBell logo/brand
 * - Navigation menu (7 items)
 * - User profile section (bottom)
 * 
 * Responsive:
 * - Desktop (1024px+): Full width with text labels
 * - Tablet (768px-1023px): Collapsed to icon-only (with hover expand)
 * - Mobile: Drawer overlay or hamburger menu
 */
export default memo(function Sidebar({
  onNavigate,
  userProfile,
}: SidebarProps) {
  const menuItems: MenuItem[] = [
    { id: 'dashboard', label: 'Dashboard', icon: Home, route: '/' },
    { id: 'orders', label: 'Orders', icon: ShoppingCart, route: '/orders' },
    { id: 'menu', label: 'Menu Management', icon: Utensils, route: '/menu' },
    { id: 'payments', label: 'Payments', icon: CreditCard, route: '/payments' },
    { id: 'analytics', label: 'Analytics', icon: BarChart3, route: '/analytics' },
    { id: 'settings', label: 'Settings', icon: Settings, route: '/settings' },
    { id: 'support', label: 'Support', icon: HelpCircle, route: '/support' },
  ];

  return (
    <div className="flex flex-col h-full bg-white dark:bg-slate-900">
      {/* Logo/Brand Section */}
      <div className="flex items-center gap-3 px-6 py-6 border-b border-slate-200 dark:border-slate-800">
        <a href="/" className="flex items-center gap-3 hover:opacity-80 transition-opacity" aria-label="ReadyBell Dashboard home">
          <div className="w-10 h-10 bg-blue-600 dark:bg-blue-500 rounded-lg flex items-center justify-center flex-shrink-0">
            <span className="text-white font-bold" aria-hidden="true">RB</span>
          </div>
          <div className="hidden md:block min-w-0">
            <p className="font-bold text-sm truncate">ReadyBell</p>
            <p className="text-xs text-slate-500 dark:text-slate-400 truncate">Restaurant Panel</p>
          </div>
        </a>
      </div>

      {/* Navigation Menu */}
      <nav className="flex-1 px-3 py-6 space-y-2 overflow-y-auto" id="main-navigation" aria-label="Main navigation">
        {menuItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.id}
              to={item.route}
              onClick={() => onNavigate?.(item.route)}
              className={({ isActive }: { isActive: boolean }) =>
                `flex items-center gap-3 px-3 py-2 rounded-lg transition-colors group relative
                ${
                  isActive
                    ? 'bg-blue-50 dark:bg-slate-800 text-blue-600 dark:text-blue-400'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                }
              `
              }
            >
              {({ isActive }: { isActive: boolean }) => (
                <>
                  <Icon className="w-5 h-5 flex-shrink-0" aria-hidden="true" />
                  <span className="hidden md:inline text-sm font-medium truncate">{item.label}</span>
                  
                  {/* Tooltip for mobile/collapsed state */}
                  <div 
                    className="md:hidden absolute left-full ml-2 px-3 py-2 bg-slate-900 dark:bg-slate-700 text-white text-xs rounded whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity z-50"
                    role="tooltip"
                  >
                    {item.label}
                  </div>
                  {isActive && <span className="sr-only">(current page) - Press Tab to move to main content</span>}
                </>
              )}
            </NavLink>
          );
        })}
      </nav>

      {/* User Profile Section */}
      <section className="border-t border-slate-200 dark:border-slate-800 p-4" aria-label="User profile">
        {userProfile ? (
          <div className="flex items-center gap-3 mb-3">
            {userProfile.avatarUrl ? (
              <img
                src={userProfile.avatarUrl}
                alt={userProfile.name}
                className="w-10 h-10 rounded-full flex-shrink-0 object-cover"
              />
            ) : (
              <div 
                className="w-10 h-10 bg-blue-200 dark:bg-blue-900 rounded-full flex items-center justify-center flex-shrink-0"
                aria-label={`Avatar for ${userProfile.name}`}
              >
                <span className="text-sm font-bold text-blue-700 dark:text-blue-300">
                  {userProfile.name
                    .split(' ')
                    .map((n) => n[0])
                    .join('')
                    .toUpperCase()
                    .slice(0, 2)}
                </span>
              </div>
            )}
            <div className="hidden md:block min-w-0 flex-1">
              <p className="font-medium text-sm truncate">{userProfile.name}</p>
              <p className="text-xs text-slate-500 dark:text-slate-400 truncate">
                {userProfile.restaurantName}
              </p>
              <p className="text-xs text-slate-400 dark:text-slate-500">{userProfile.role}</p>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-3 mb-3">
            <div className="w-10 h-10 bg-slate-200 dark:bg-slate-800 rounded-full flex-shrink-0 animate-pulse" />
            <div className="hidden md:block flex-1 space-y-1">
              <div className="h-3 bg-slate-200 dark:bg-slate-800 rounded animate-pulse" />
              <div className="h-2 bg-slate-200 dark:bg-slate-800 rounded animate-pulse w-3/4" />
            </div>
          </div>
        )}

        {/* Logout button */}
        <button
          className="w-full flex items-center gap-3 px-3 py-2 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors group focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
          aria-label="Logout from dashboard"
          onClick={signOut}
          type="button"
        >
          <LogOut className="w-5 h-5 flex-shrink-0" aria-hidden="true" />
          <span className="hidden md:inline text-sm font-medium">Logout</span>
          
          {/* Tooltip for mobile/collapsed state */}
          <div 
            className="md:hidden absolute left-full ml-2 px-3 py-2 bg-slate-900 dark:bg-slate-700 text-white text-xs rounded whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity z-50"
            role="tooltip"
          >
            Logout
          </div>
        </button>
      </section>
    </div>
  );
});
