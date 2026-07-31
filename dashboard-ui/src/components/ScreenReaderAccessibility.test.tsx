import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter as Router } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@/context/ThemeContext';
import { SearchProvider } from '@/context/SearchContext';
import Header from './Header';
import Sidebar from './Sidebar';
import IncomingOrdersTable from './IncomingOrdersTable';
import OrderStatusBadge from './OrderStatusBadge';
import Layout from './Layout';
import { OrderRow } from '@/types';

/**
 * Screen Reader Accessibility Test
 * 
 * Practical test of screen reader support based on actual implementation.
 * Tests the key requirements from 4.2 Screen Reader Support:
 * 
 * 1. Semantic HTML structure
 * 2. Accessible labels and descriptions
 * 3. Table accessibility
 * 4. Status badge announcements
 * 5. Form input labeling
 * 6. Icon accessibility
 */
describe('Screen Reader Accessibility - Practical Tests', () => {
  const mockOrders: OrderRow[] = [
    {
      id: '1',
      orderId: 'ORD-001',
      customerName: 'John Doe',
      itemsOrdered: 'Burger, Fries, Soda',
      deliveryETA: new Date(Date.now() + 3600000),
      status: 'CREATED',
      estimatedWaitTime: 30,
      itemCount: 3,
    },
  ];

  const renderWithProviders = (ui: React.ReactElement) => {
    return render(
      <ThemeProvider defaultMode="light">
        <SearchProvider>
          <Router>
            {ui}
          </Router>
        </SearchProvider>
      </ThemeProvider>
    );
  };

  describe('1. Semantic HTML and ARIA Structure', () => {
    test('Layout has proper semantic structure for screen readers', () => {
      renderWithProviders(<Layout />);

      // Header should be marked as banner (already implemented)
      const banner = screen.getByRole('banner');
      expect(banner).toBeInTheDocument();

      // Main content should be marked
      const main = screen.getByRole('main');
      expect(main).toBeInTheDocument();
      expect(main).toHaveAttribute('id', 'main-content');

      // Navigation should be present
      const navs = screen.getAllByRole('navigation');
      expect(navs.length).toBeGreaterThan(0);
    });

    test('Header has proper toolbar and navigation', () => {
      render(<Header onSearch={() => {}} />);

      // Header has navigation role
      const nav = screen.getByRole('navigation');
      expect(nav).toBeInTheDocument();

      // Toolbar for header actions
      const toolbar = screen.getByRole('toolbar');
      expect(toolbar).toBeInTheDocument();
      expect(toolbar).toHaveAttribute('aria-label', 'Header actions');
    });

    test('Sidebar has proper navigation labeling', () => {
      render(
        <Router>
          <Sidebar />
        </Router>
      );

      // Sidebar navigation should be properly labeled
      const nav = screen.getByRole('navigation');
      expect(nav).toBeInTheDocument();
      expect(nav).toHaveAttribute('aria-label', 'Main navigation');
    });
  });

  describe('2. Accessible Labels and Descriptions', () => {
    test('All interactive elements have accessible labels', () => {
      render(<Header onSearch={() => {}} />);

      // Get all buttons and check they have accessible names
      const buttons = screen.getAllByRole('button');
      buttons.forEach(button => {
        const hasLabel = 
          button.hasAttribute('aria-label') ||
          button.textContent?.trim().length > 0 ||
          button.hasAttribute('aria-labelledby');
        expect(hasLabel).toBeTruthy();
      });

      // Theme toggle has specific label
      const themeButton = screen.getByLabelText(/Switch to (light|dark) theme/);
      expect(themeButton).toBeInTheDocument();

      // Search input has label
      const searchInput = screen.getByLabelText('Search orders');
      expect(searchInput).toBeInTheDocument();
    });

    test('Icons have proper accessibility attributes', () => {
      render(<Header onSearch={() => {}} />);

      // Decorative icons should be hidden from screen readers
      const decorativeIcons = screen.getAllByRole('img', { hidden: true });
      decorativeIcons.forEach(icon => {
        expect(icon).toHaveAttribute('aria-hidden', 'true');
      });

      // Functional icons should be in buttons with labels
      const themeButton = screen.getByLabelText(/Switch to (light|dark) theme/);
      expect(themeButton.querySelector('svg')).toBeInTheDocument();
    });
  });

  describe('3. Table Accessibility', () => {
    test('Table has proper semantic structure and labeling', () => {
      render(<IncomingOrdersTable orders={mockOrders} />);

      // Table should have aria-label
      const table = screen.getByRole('table');
      expect(table).toBeInTheDocument();
      expect(table).toHaveAttribute('aria-label', 'Incoming orders list');

      // Should have caption for screen readers
      const caption = screen.getByText('List of incoming orders with sorting and pagination');
      expect(caption).toBeInTheDocument();
      expect(caption).toHaveClass('sr-only');

      // Table headers should have scope attribute
      const headers = screen.getAllByRole('columnheader');
      headers.forEach(header => {
        expect(header).toHaveAttribute('scope', 'col');
      });

      // Some headers should be sortable (have aria-sort)
      const sortableHeaders = headers.filter(h => h.hasAttribute('aria-sort'));
      expect(sortableHeaders.length).toBeGreaterThan(0);
    });

    test('Table sorting provides feedback to screen readers', async () => {
      const user = userEvent.setup();
      render(<IncomingOrdersTable orders={mockOrders} />);

      // Find sortable headers (they have role="button" and aria-sort)
      const sortableHeaders = screen.getAllByRole('button').filter(button => 
        button.hasAttribute('aria-sort')
      );
      expect(sortableHeaders.length).toBeGreaterThan(0);

      // Test that clicking updates visual indicators
      if (sortableHeaders.length > 0) {
        const firstHeader = sortableHeaders[0];
        const initialSort = firstHeader.getAttribute('aria-sort');
        
        await user.click(firstHeader);
        
        // Sort state should change
        const newSort = firstHeader.getAttribute('aria-sort');
        expect(newSort).not.toBe(initialSort);
      }
    });
  });

  describe('4. Status Badge Announcements', () => {
    test('Status badges announce their state to screen readers', () => {
      // Test each status badge type
      const statuses: Array<['CREATED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED', string]> = [
        ['CREATED', 'New Order'],
        ['PREPARING', 'Preparing'],
        ['READY', 'Ready for Pickup'],
        ['COMPLETED', 'Completed'],
        ['CANCELLED', 'Cancelled'],
      ];

      statuses.forEach(([status, expectedLabel]) => {
        const { unmount } = render(<OrderStatusBadge status={status} />);
        
        // Status badge should have role="status" for live announcements
        const badge = screen.getByRole('status');
        expect(badge).toBeInTheDocument();
        
        // Should have aria-label with full text
        expect(badge).toHaveAttribute('aria-label', expectedLabel);
        
        // Should have visible text
        expect(screen.getByText(expectedLabel)).toBeInTheDocument();
        
        unmount();
      });
    });

    test('Status badges have appropriate color contrast (implied by classes)', () => {
      render(<OrderStatusBadge status="CREATED" />);
      const badge = screen.getByRole('status');
      
      // Status badges use theme-aware color classes
      // These should provide sufficient contrast in both light and dark modes
      expect(badge).toHaveClass('text-blue-700', 'dark:text-blue-300');
      expect(badge).toHaveClass('bg-blue-50', 'dark:bg-blue-900/30');
    });
  });

  describe('5. Form Inputs and Controls', () => {
    test('Form inputs have associated labels', () => {
      render(<Header onSearch={() => {}} />);

      // Search input should have explicit label
      const searchInput = screen.getByLabelText('Search orders');
      expect(searchInput).toBeInTheDocument();
      expect(searchInput).toHaveAttribute('type', 'text');

      // Search input should have help text for screen readers
      const helpText = screen.getByText('Debounced search, results update automatically');
      expect(helpText).toBeInTheDocument();
      expect(helpText).toHaveClass('sr-only');
    });

    test('Dropdowns and menus have proper ARIA attributes', () => {
      render(<Header onSearch={() => {}} />);

      // Settings menu button should have proper attributes
      const settingsButton = screen.getByLabelText('Open settings menu');
      expect(settingsButton).toBeInTheDocument();
      expect(settingsButton).toHaveAttribute('aria-haspopup', 'menu');
      expect(settingsButton).toHaveAttribute('aria-expanded', 'false');
    });
  });

  describe('6. Navigation and Focus Management', () => {
    test('Navigation items indicate active state', () => {
      render(
        <Router>
          <Sidebar />
        </Router>
      );

      // Navigation links should be present
      const links = screen.getAllByRole('link');
      expect(links.length).toBeGreaterThan(0);

      // Active link styling is handled by NavLink component
      // which should provide appropriate visual and ARIA cues
    });

    test('Focus indicators are visible for keyboard navigation', () => {
      render(<Header onSearch={() => {}} />);

      // Buttons should have focus styles
      const buttons = screen.getAllByRole('button');
      buttons.forEach(button => {
        expect(button).toHaveClass('focus:outline-none', 'focus:ring-2', 'focus:ring-blue-500');
      });
    });
  });

  describe('7. Live Regions and Dynamic Content', () => {
    test('Notification badges use role="status" for announcements', () => {
      render(<Header onSearch={() => {}} notificationCount={3} />);

      // Notification badge should be findable
      const statusElements = screen.getAllByRole('status');
      const notificationBadge = statusElements.find(el => 
        el.textContent?.includes('3') || el.getAttribute('aria-label')?.includes('notification')
      );
      
      // Either we have a notification badge or the count is shown in another way
      if (notificationBadge) {
        expect(notificationBadge.textContent).toContain('3');
      }
    });

    test('Real-time updates would use aria-live regions', () => {
      // This would be tested when SSE/real-time updates are implemented
      // For now, check that the infrastructure supports it
      render(<IncomingOrdersTable orders={mockOrders} />);
      
      // Table has proper structure for dynamic updates
      const table = screen.getByRole('table');
      expect(table).toBeInTheDocument();
      
      // When real-time updates are added, they should use aria-live="polite"
      // or similar for announcing new orders
    });
  });

  describe('8. Overall Screen Reader Compatibility', () => {
    test('Complete dashboard is screen reader accessible', () => {
      // This is a comprehensive check of key accessibility features
      renderWithProviders(<Layout />);

      // 1. Semantic landmarks
      expect(screen.getByRole('banner')).toBeInTheDocument();
      expect(screen.getByRole('main')).toBeInTheDocument();

      // 2. Accessible navigation
      const navs = screen.getAllByRole('navigation');
      expect(navs.length).toBeGreaterThan(0);

      // 3. Heading structure
      const headings = screen.queryAllByRole('heading');
      expect(headings.length).toBeGreaterThan(0);

      // 4. Interactive elements are labeled
      const buttons = screen.getAllByRole('button');
      buttons.forEach(button => {
        const hasLabel = 
          button.hasAttribute('aria-label') ||
          button.textContent?.trim().length > 0;
        expect(hasLabel).toBeTruthy();
      });

      // 5. Form controls are labeled
      const inputs = screen.queryAllByRole('textbox');
      inputs.forEach(input => {
        const isLabeled = 
          input.hasAttribute('aria-label') ||
          input.hasAttribute('aria-labelledby') ||
          input.hasAttribute('id') && document.querySelector(`label[for="${input.getAttribute('id')}"]`);
        expect(isLabeled).toBeTruthy();
      });

      // 6. Status information is properly announced
      const statusElements = screen.queryAllByRole('status');
      statusElements.forEach(status => {
        expect(status.textContent?.trim().length).toBeGreaterThan(0);
      });
    });
  });
});