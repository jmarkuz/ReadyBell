import React, { ReactNode } from 'react';
import { Outlet } from 'react-router-dom';
import Header from './Header';
import Sidebar from './Sidebar';

interface LayoutProps {
  children?: ReactNode;
}

/**
 * Layout Component
 * 
 * Main layout wrapper for the dashboard that structures content into:
 * - Fixed/sticky header at top
 * - Sidebar on left (collapsible on mobile)
 * - Main content area (scrollable independently)
 * 
 * Responsive:
 * - Desktop (1024px+): Full sidebar visible, 4-column content
 * - Tablet (768px-1023px): Collapsed sidebar, 2-column content
 * - Mobile (<768px): Sidebar as drawer/overlay, 1-column stacked
 */
export default function Layout({ children }: LayoutProps) {
  const [sidebarOpen, setSidebarOpen] = React.useState(true);

  return (
    <div className="flex h-screen bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-50">
      {/* Sidebar - Hidden on mobile, toggleable on tablet, always visible on desktop */}
      <aside
        className={`
          fixed left-0 top-0 z-40 h-screen w-64
          bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800
          transition-transform duration-300 ease-in-out
          lg:relative lg:translate-x-0
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}
          md:w-20 md:hover:w-64
          overflow-y-auto
        `}
      >
        <Sidebar onToggleCollapse={() => setSidebarOpen(!sidebarOpen)} />
      </aside>

      {/* Overlay for mobile when sidebar is open */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Main layout: Header + Content */}
      <div className="flex flex-col flex-1 overflow-hidden">
        {/* Header - Fixed at top */}
        <header className="sticky top-0 z-20 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
          <Header onMenuToggle={() => setSidebarOpen(!sidebarOpen)} />
        </header>

        {/* Main Content Area - Scrollable independently */}
        <main className="flex-1 overflow-y-auto bg-slate-50 dark:bg-slate-950">
          <div className="container mx-auto px-4 py-8 max-w-7xl">
            {/* If children provided, render them; otherwise use React Router Outlet */}
            {children || <Outlet />}
          </div>
        </main>
      </div>
    </div>
  );
}
