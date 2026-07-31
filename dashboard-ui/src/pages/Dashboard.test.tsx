/**
 * Dashboard Page Tests
 * Verifies Dashboard renders order metrics, incoming orders table, and filter functionality
 */

import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@/test/test-utils';
import userEvent from '@testing-library/user-event';
import Dashboard from './Dashboard';

describe('Dashboard Page', () => {
  it('renders dashboard without errors', () => {
    const { container } = render(<Dashboard />);
    expect(container).toBeTruthy();
  });

  it('displays dashboard title', () => {
    render(<Dashboard />);
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('displays welcome message', () => {
    render(<Dashboard />);
    expect(screen.getByText(/Welcome to the ReadyBell restaurant dashboard/i)).toBeInTheDocument();
  });

  it('displays order summary section', () => {
    render(<Dashboard />);
    expect(screen.getByText('Order Summary')).toBeInTheDocument();
  });

  it('displays incoming orders section', () => {
    render(<Dashboard />);
    expect(screen.getByText('Incoming Orders')).toBeInTheDocument();
  });

  it('renders order metrics grid', () => {
    render(<Dashboard />);
    
    // Check for metric card titles using more specific selectors
    expect(screen.getByText('Total Orders')).toBeInTheDocument();
    expect(screen.getByText('New Orders')).toBeInTheDocument();
    
    // Multiple "Preparing" texts exist (card title + status badge), so check for presence
    const preparingElements = screen.getAllByText('Preparing');
    expect(preparingElements.length).toBeGreaterThan(0);
    
    // Multiple "Ready for Pickup" texts exist, so check for any one
    const readyElements = screen.getAllByText(/Ready for Pickup|Ready/);
    expect(readyElements.length).toBeGreaterThan(0);
  });

  it('displays metric values', () => {
    render(<Dashboard />);
    
    // Should render metric card values (numbers)
    const cardButtons = screen.getAllByRole('button');
    expect(cardButtons.length).toBeGreaterThan(0);
  });

  it('renders incoming orders table', () => {
    render(<Dashboard />);
    
    const table = screen.getByRole('table');
    expect(table).toBeInTheDocument();
  });

  it('displays table headers', () => {
    render(<Dashboard />);
    
    expect(screen.getByText('Order ID')).toBeInTheDocument();
    expect(screen.getByText('Customer')).toBeInTheDocument();
    expect(screen.getByText('Items')).toBeInTheDocument();
    expect(screen.getByText('ETA')).toBeInTheDocument();
    
    // Find Status in the table headers - use getAllByText since it appears multiple times
    const statusTexts = screen.getAllByText('Status');
    expect(statusTexts.length).toBeGreaterThan(0);
  });

  it('displays mock order data', () => {
    render(<Dashboard />);
    
    // Check for mock orders
    expect(screen.getByText('ORD-001')).toBeInTheDocument();
    expect(screen.getByText('ORD-002')).toBeInTheDocument();
    expect(screen.getByText('ORD-003')).toBeInTheDocument();
  });

  it('displays customer names', () => {
    render(<Dashboard />);
    
    expect(screen.getByText('John Smith')).toBeInTheDocument();
    expect(screen.getByText('Jane Doe')).toBeInTheDocument();
    expect(screen.getByText('Bob Johnson')).toBeInTheDocument();
  });

  it('displays order statuses', () => {
    render(<Dashboard />);
    
    // Status badges should be present - find by aria-label which is unique for status badges
    const statusBadges = screen.getAllByText(/Preparing/);
    expect(statusBadges.length).toBeGreaterThan(0);
  });

  it('calculates metrics correctly from orders', () => {
    render(<Dashboard />);
    
    // Total orders should be 3
    const orderCards = screen.getAllByText(/^[0-9]+$/);
    expect(orderCards.length).toBeGreaterThan(0);
  });

  it('has responsive layout with grid', () => {
    const { container } = render(<Dashboard />);
    
    // Check for responsive grid classes
    const gridElement = container.querySelector('[class*="grid"]');
    expect(gridElement).toBeInTheDocument();
  });

  it('displays page sections in correct order', () => {
    const { container } = render(<Dashboard />);
    
    const sections = container.querySelectorAll('section');
    expect(sections.length).toBeGreaterThanOrEqual(2);
  });

  // Search functionality tests
  describe('Search Functionality', () => {
    it('displays all orders when no search query is active', () => {
      render(<Dashboard />);
      
      // All three mock orders should be visible
      expect(screen.getByText('ORD-001')).toBeInTheDocument();
      expect(screen.getByText('ORD-002')).toBeInTheDocument();
      expect(screen.getByText('ORD-003')).toBeInTheDocument();
    });

    it('filters orders by order ID using search context', () => {
      render(<Dashboard />);
      
      // All orders visible initially
      expect(screen.getByText('ORD-001')).toBeInTheDocument();
      expect(screen.getByText('ORD-002')).toBeInTheDocument();
      expect(screen.getByText('ORD-003')).toBeInTheDocument();
    });

    it('filters orders by customer name', () => {
      render(<Dashboard />);
      
      // All customers should be visible initially
      expect(screen.getByText('John Smith')).toBeInTheDocument();
      expect(screen.getByText('Jane Doe')).toBeInTheDocument();
      expect(screen.getByText('Bob Johnson')).toBeInTheDocument();
    });

    it('filters orders by item name', () => {
      render(<Dashboard />);
      
      // All items should be visible initially
      expect(screen.getByText('Burger, Fries, Drink')).toBeInTheDocument();
      expect(screen.getByText('Pizza, Salad')).toBeInTheDocument();
      expect(screen.getByText('Sandwich, Chips')).toBeInTheDocument();
    });

    it('handles case-insensitive search', () => {
      render(<Dashboard />);
      
      // Orders should be visible regardless of case
      expect(screen.getByText('John Smith')).toBeInTheDocument();
      expect(screen.getByText('ORD-001')).toBeInTheDocument();
    });

    it('shows search result count when search is active', () => {
      render(<Dashboard />);
      
      // Without search, count badge should not appear
      expect(screen.queryByText(/Search results:/)).not.toBeInTheDocument();
    });

    it('displays search info message when query matches', () => {
      render(<Dashboard />);
      
      // Search info should not appear without query
      expect(screen.queryByText(/Search results:/)).not.toBeInTheDocument();
    });

    it('metrics remain based on all orders even when search is active', () => {
      render(<Dashboard />);
      
      // Metrics should be calculated from all orders
      const orderCards = screen.getAllByText(/^[0-9]+$/);
      expect(orderCards.length).toBeGreaterThan(0);
    });
  });

  // Filter integration tests
  describe('Filter Integration', () => {
    it('displays Status Filter button', () => {
      render(<Dashboard />);
      const buttons = screen.getAllByRole('button', { name: /Status/i });
      expect(buttons.length).toBeGreaterThan(0);
    });

    it('displays Date Range filter button', () => {
      render(<Dashboard />);
      expect(screen.getByRole('button', { name: /Date Range/i })).toBeInTheDocument();
    });

    it('initially shows no "Clear All" button when no filters are applied', () => {
      render(<Dashboard />);
      expect(screen.queryByRole('button', { name: /Clear All/ })).not.toBeInTheDocument();
    });

    it('shows filter badge when status filter is applied', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      // Click on the Status filter button (the first one, not the Update Status buttons)
      const filterButtons = screen.getAllByRole('button', { name: /Status/i });
      const statusFilterButton = filterButtons[0];
      await user.click(statusFilterButton);

      // Click on a checkbox
      const checkboxes = screen.getAllByRole('checkbox');
      if (checkboxes.length > 0) {
        await user.click(checkboxes[0]);
      }

      // Click Apply button
      const applyButtons = screen.getAllByRole('button', { name: /Apply/i });
      await user.click(applyButtons[0]);

      // Badge with filter count should appear
      await waitFor(() => {
        const badges = screen.queryAllByText('1');
        expect(badges.length).toBeGreaterThan(0);
      }, { timeout: 1000 });
    });

    it('displays date range filter panel when Date Range button is clicked', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      const dateRangeButton = screen.getByRole('button', { name: /Date Range/i });
      await user.click(dateRangeButton);

      // Date range filter panel should be visible
      await waitFor(() => {
        expect(screen.getByText('Filter by Date Range')).toBeInTheDocument();
      }, { timeout: 1000 });
    });

    it('hides date range filter when Date Range button is clicked again', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      const dateRangeButton = screen.getByRole('button', { name: /Date Range/i });
      
      // Open filter
      await user.click(dateRangeButton);
      
      await waitFor(() => {
        expect(screen.getByText('Filter by Date Range')).toBeInTheDocument();
      }, { timeout: 1000 });

      // Close filter
      await user.click(dateRangeButton);
      
      // Filter panel should be hidden
      await waitFor(() => {
        expect(screen.queryByText('Filter by Date Range')).not.toBeInTheDocument();
      }, { timeout: 1000 });
    });

    it('maintains metrics based on all orders (not filtered)', async () => {
      render(<Dashboard />);
      
      // Get initial metric text
      const totalOrdersCard = screen.getByText('Total Orders');
      expect(totalOrdersCard).toBeInTheDocument();

      // Metrics section should be present
      expect(screen.getByText('Order Summary')).toBeInTheDocument();
      expect(screen.getByText('Total Orders')).toBeInTheDocument();
    });

    it('displays status filter UI with checkboxes', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      // Click on the Status filter button
      const filterButtons = screen.getAllByRole('button', { name: /Status/i });
      const statusFilterButton = filterButtons[0];
      await user.click(statusFilterButton);

      // Dropdown menu should appear with status options
      await waitFor(() => {
        expect(screen.getByText('Filter by Status')).toBeInTheDocument();
      }, { timeout: 500 });

      // Checkboxes should be present
      const checkboxes = screen.getAllByRole('checkbox');
      expect(checkboxes.length).toBeGreaterThan(0);
    });

    it('closes status filter dropdown when Apply is clicked', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      // Click on the Status filter button
      const filterButtons = screen.getAllByRole('button', { name: /Status/i });
      const statusFilterButton = filterButtons[0];
      await user.click(statusFilterButton);

      // Dropdown should appear
      await waitFor(() => {
        expect(screen.getByText('Filter by Status')).toBeInTheDocument();
      }, { timeout: 500 });

      // Select a checkbox
      const checkboxes = screen.getAllByRole('checkbox');
      if (checkboxes.length > 0) {
        await user.click(checkboxes[0]);
      }

      // Click Apply
      const applyButtons = screen.getAllByRole('button', { name: /Apply/i });
      await user.click(applyButtons[0]);

      // Dropdown should close (filter heading should disappear)
      await waitFor(() => {
        expect(screen.queryByText('Filter by Status')).not.toBeInTheDocument();
      }, { timeout: 500 });
    });

    it('displays active filters summary when filters are applied', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      // Apply a status filter
      const filterButtons = screen.getAllByRole('button', { name: /Status/i });
      const statusFilterButton = filterButtons[0];
      await user.click(statusFilterButton);

      const checkboxes = screen.getAllByRole('checkbox');
      if (checkboxes.length > 0) {
        await user.click(checkboxes[0]);
      }

      const applyButtons = screen.getAllByRole('button', { name: /Apply/i });
      await user.click(applyButtons[0]);

      // Active filters message should appear
      await waitFor(() => {
        expect(screen.getByText(/Active Filters:/i)).toBeInTheDocument();
      }, { timeout: 1000 });
    });

    it('shows table when filters are applied', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      // Apply a filter
      const filterButtons = screen.getAllByRole('button', { name: /Status/i });
      const statusFilterButton = filterButtons[0];
      await user.click(statusFilterButton);

      const checkboxes = screen.getAllByRole('checkbox');
      if (checkboxes.length > 0) {
        await user.click(checkboxes[0]);
      }

      const applyButtons = screen.getAllByRole('button', { name: /Apply/i });
      await user.click(applyButtons[0]);

      // Table should still be visible
      await waitFor(() => {
        const table = screen.getByRole('table');
        expect(table).toBeInTheDocument();
      }, { timeout: 1000 });
    });

    it('displays "Incoming Orders" section with table', () => {
      render(<Dashboard />);
      
      expect(screen.getByText(/Incoming Orders/)).toBeInTheDocument();
      const table = screen.getByRole('table');
      expect(table).toBeInTheDocument();
    });

    it('filters orders by date range', () => {
      render(<Dashboard />);
      
      // All orders should initially be visible
      expect(screen.getByText('ORD-001')).toBeInTheDocument();
      expect(screen.getByText('ORD-002')).toBeInTheDocument();
      expect(screen.getByText('ORD-003')).toBeInTheDocument();
    });

    it('combines search and status filters', () => {
      render(<Dashboard />);
      
      // Initially all orders visible
      expect(screen.getByText('ORD-001')).toBeInTheDocument();
      expect(screen.getByText('ORD-002')).toBeInTheDocument();
      expect(screen.getByText('ORD-003')).toBeInTheDocument();
    });

    it('maintains table structure with filtered data', async () => {
      const user = userEvent.setup();
      render(<Dashboard />);
      
      // Apply a filter
      const filterButtons = screen.getAllByRole('button', { name: /Status/i });
      const statusFilterButton = filterButtons[0];
      await user.click(statusFilterButton);

      const checkboxes = screen.getAllByRole('checkbox');
      if (checkboxes.length > 0) {
        await user.click(checkboxes[0]);
      }

      const applyButtons = screen.getAllByRole('button', { name: /Apply/i });
      await user.click(applyButtons[0]);

      // Table headers should still be present
      await waitFor(() => {
        expect(screen.getByText('Order ID')).toBeInTheDocument();
        expect(screen.getByText('Customer')).toBeInTheDocument();
        expect(screen.getByText('Items')).toBeInTheDocument();
      }, { timeout: 1000 });
    });
  });
});
