import { useMemo, useState, useCallback } from 'react';
import OrderMetricsGrid from '@/components/OrderMetricsGrid';
import IncomingOrdersTable from '@/components/IncomingOrdersTable';
import StatusFilter from '@/components/StatusFilter';
import { DateRangeFilter } from '@/components/DateRangeFilter';
import { useSearch } from '@/context/SearchContext';
import { OrderMetrics, OrderStatus } from '@/types';
import { useDashboardOrders } from '@/hooks/useDashboardOrders';
import { apiClient } from '@/utils/api';

/**
 * Dashboard Page
 * 
 * Main dashboard view with:
 * - Order summary cards grid (4 cards: Total, New, Preparing, Ready)
 * - Incoming orders table with search and filter functionality
 * - Search integration with header
 * - Status filter dropdown
 * - Date range filter
 * 
 * Features:
 * - Search filters by orderId, customerName, or itemsOrdered (case-insensitive, partial match)
 * - Status filter: select multiple statuses (CREATED, PREPARING, READY, COMPLETED)
 * - Date range filter: filter by order creation date with presets and custom dates
 * - Data: newest 100 orders from GET /api/orders, refreshed on SSE events (polling while offline)
 * - Metrics calculated from all loaded orders (not filtered)
 * - Table displays filtered orders based on search, status, and date range
 * - Filter badges show active filter counts
 * 
 * Responsive:
 * - Desktop (1024px+): 4-column card grid
 * - Tablet (768px-1023px): 2-column card grid
 * - Mobile (<768px): 1-column stacked
 */
export default function Dashboard() {
  // Get search query from context
  const { query: searchQuery } = useSearch();

  // Filter state
  const [selectedStatuses, setSelectedStatuses] = useState<OrderStatus[]>([]);
  const [dateRangeStart, setDateRangeStart] = useState<Date | null>(null);
  const [dateRangeEnd, setDateRangeEnd] = useState<Date | null>(null);
  const [showDateFilter, setShowDateFilter] = useState(false);

  // Mock initial orders data
  // Orders come from the backend (GET /api/orders) and are refreshed on SSE events
  const { orders: displayOrders, isLoading, error: loadError, isConnected, reload } = useDashboardOrders();
  const [actionError, setActionError] = useState<string | null>(null);

  // Dev-only test data tools (the backend endpoints exist under the `dev` profile only)
  const [testDataBusy, setTestDataBusy] = useState(false);
  const [testDataMessage, setTestDataMessage] = useState<string | null>(null);

  const runTestData = useCallback(async (action: () => Promise<string>) => {
    setTestDataBusy(true);
    setTestDataMessage(null);
    try {
      setTestDataMessage(await action());
    } catch (e) {
      setTestDataMessage(e instanceof Error ? e.message : 'Test data request failed.');
    } finally {
      setTestDataBusy(false);
      await reload();
    }
  }, [reload]);

  const handleGenerateTestData = useCallback(
    () => runTestData(async () => `Added ${(await apiClient.generateTestData()).created} test orders.`),
    [runTestData]
  );
  const handleClearTestData = useCallback(
    () => runTestData(async () => `Removed ${(await apiClient.clearTestData()).deleted} test orders.`),
    [runTestData]
  );

  const handleStatusChange = useCallback(async (orderId: string, newStatus: OrderStatus) => {
    if (newStatus === 'CREATED') return; // no transition leads back to CREATED
    setActionError(null);
    try {
      await apiClient.updateOrderStatus(orderId, newStatus);
      await reload();
    } catch (e) {
      // e.g. 409 (changed by someone else) or 422 (transition no longer allowed): show it and resync
      setActionError(e instanceof Error ? e.message : 'Could not update the order status.');
      await reload();
    }
  }, [reload]);


  // Filter orders based on search query, status, and date range
  const filteredOrders = useMemo(() => {
    let result = [...displayOrders];

    // Apply search filter
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      result = result.filter((order) => {
        return (
          order.orderId.toLowerCase().includes(query) ||
          (order.customerName?.toLowerCase().includes(query) ?? false) ||
          order.itemsOrdered.toLowerCase().includes(query)
        );
      });
    }

    // Apply status filter
    if (selectedStatuses.length > 0) {
      result = result.filter((order) => selectedStatuses.includes(order.status));
    }

    // Apply date range filter
    if (dateRangeStart && dateRangeEnd) {
      result = result.filter((order) => {
        const orderDate = order.deliveryETA;
        // Orders without an ETA cannot match a date range
        return orderDate !== null && orderDate >= dateRangeStart && orderDate <= dateRangeEnd;
      });
    }

    return result;
  }, [displayOrders, searchQuery, selectedStatuses, dateRangeStart, dateRangeEnd]);

  // Calculate metrics from ALL orders (not filtered)
  const metrics: OrderMetrics = useMemo(() => {
    return {
      totalOrders: displayOrders.length,
      newOrders: displayOrders.filter((o) => o.status === 'CREATED').length,
      preparing: displayOrders.filter((o) => o.status === 'PREPARING').length,
      readyForPickup: displayOrders.filter((o) => o.status === 'READY').length,
      completed: displayOrders.filter((o) => o.status === 'COMPLETED').length,
      cancelled: displayOrders.filter((o) => o.status === 'CANCELLED').length,
    };
  }, [displayOrders]);

  // Count active filters
  const activeFilterCount = (selectedStatuses.length > 0 ? 1 : 0) + (dateRangeStart && dateRangeEnd ? 1 : 0);

  // Handle status filter apply
  const handleStatusFilterApply = useCallback((statuses: OrderStatus[]) => {
    setSelectedStatuses(statuses);
  }, []);

  // Handle date range filter apply
  const handleDateRangeApply = useCallback((startDate: Date, endDate: Date) => {
    setDateRangeStart(startDate);
    setDateRangeEnd(endDate);
  }, []);

  // Handle clear all filters
  const handleClearAllFilters = useCallback(() => {
    setSelectedStatuses([]);
    setDateRangeStart(null);
    setDateRangeEnd(null);
    setShowDateFilter(false);
  }, []);

  return (
    <div className="space-y-8">
      {/* Page title with SSE connection indicator */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Dashboard</h1>
          <p className="text-slate-600 dark:text-slate-400 mt-2">
            Welcome to the ReadyBell restaurant dashboard. Monitor incoming orders and track operations.
          </p>
        </div>
        
        {/* Real-time connection status indicator */}
        <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-100 dark:bg-slate-800">
          <div 
            className={`w-2 h-2 rounded-full ${
              isConnected 
                ? 'bg-green-500 animate-pulse' 
                : 'bg-red-500'
            }`}
            aria-label={isConnected ? 'Real-time connection active' : 'Real-time connection disconnected'}
          />
          <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
            {isConnected ? 'Live' : 'Offline'}
          </span>
          {loadError && (
            <span className="text-xs text-red-600 dark:text-red-400 ml-2" role="alert">
              ({loadError.message})
            </span>
          )}
        </div>
      </div>

      {/* Dev-only: generate / remove test data */}
      {import.meta.env.DEV && (
        <section aria-label="Test data" className="flex items-center gap-3 flex-wrap">
          <button
            type="button"
            onClick={handleGenerateTestData}
            disabled={testDataBusy}
            className="px-4 py-2 text-sm font-medium bg-stone-800 text-white dark:bg-stone-100 dark:text-stone-900 rounded-full hover:opacity-90 disabled:opacity-60 transition-opacity focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
          >
            Generate test data
          </button>
          <button
            type="button"
            onClick={handleClearTestData}
            disabled={testDataBusy}
            className="px-4 py-2 text-sm font-medium border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-slate-200 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-60 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
          >
            Delete test data
          </button>
          {testDataMessage && (
            <span className="text-sm text-slate-600 dark:text-slate-400" role="status">
              {testDataMessage}
            </span>
          )}
        </section>
      )}

      {/* Order Summary Cards Grid */}
      <section aria-labelledby="order-summary-heading">
        <h2 id="order-summary-heading" className="text-lg font-semibold text-slate-900 dark:text-white mb-4">Order Summary</h2>
        <OrderMetricsGrid metrics={metrics} />
      </section>

      {/* Filters Section */}
      <section aria-labelledby="filters-heading" className="space-y-4">
        <h2 id="filters-heading" className="text-lg font-semibold text-slate-900 dark:text-white">Filters</h2>
        <div className="space-y-4">
          {/* Filter Controls */}
          <div className="flex items-center gap-3 flex-wrap">
            <StatusFilter
              selectedStatuses={selectedStatuses}
              onApply={handleStatusFilterApply}
            />
            <button
              onClick={() => setShowDateFilter(!showDateFilter)}
              className="flex items-center gap-2 px-3 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-full hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors relative focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
              aria-label={`Date range filter ${dateRangeStart && dateRangeEnd ? '(active)' : ''}`}
              aria-expanded={showDateFilter}
              type="button"
            >
              <span className="text-sm font-medium">Date Range</span>
              {dateRangeStart && dateRangeEnd && (
                <span 
                  className="absolute -top-2 -right-2 bg-blue-600 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
                  aria-label="1 date filter active"
                >
                  1
                </span>
              )}
            </button>

            {/* Clear Filters Button */}
            {activeFilterCount > 0 && (
              <button
                onClick={handleClearAllFilters}
                className="px-3 py-2 text-sm font-medium text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
                aria-label={`Clear all ${activeFilterCount} active filters`}
                type="button"
              >
                Clear All ({activeFilterCount})
              </button>
            )}
          </div>

          {/* Date Range Filter (Expandable) */}
          {showDateFilter && (
            <div className="pl-0">
              <DateRangeFilter
                onApply={handleDateRangeApply}
                onClear={() => {
                  setDateRangeStart(null);
                  setDateRangeEnd(null);
                  setShowDateFilter(false);
                }}
                initialStartDate={dateRangeStart || undefined}
                initialEndDate={dateRangeEnd || undefined}
                isOpen={true}
              />
            </div>
          )}
        </div>

        {/* Active Filters Display */}
        {(selectedStatuses.length > 0 || (dateRangeStart && dateRangeEnd)) && (
          <div 
            className="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg"
            role="status"
            aria-live="polite"
            aria-atomic="true"
          >
            <p className="text-sm text-blue-700 dark:text-blue-300">
              <strong>Active Filters:</strong>
              {selectedStatuses.length > 0 && ` Status (${selectedStatuses.length})`}
              {selectedStatuses.length > 0 && dateRangeStart && dateRangeEnd && ', '}
              {dateRangeStart && dateRangeEnd && ` Date Range (${dateRangeStart.toLocaleDateString()} - ${dateRangeEnd.toLocaleDateString()})`}
            </p>
          </div>
        )}
      </section>

      {/* Search Results Info */}
      {searchQuery && (
        <div 
          className="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg"
          role="status"
          aria-live="polite"
          aria-atomic="true"
        >
          <p className="text-sm text-blue-700 dark:text-blue-300">
            Search results: <strong>{filteredOrders.length}</strong> order(s) found for "{searchQuery}"
          </p>
        </div>
      )}

      {/* Incoming Orders Table */}
      <section aria-labelledby="orders-heading">
        <h2 id="orders-heading" className="text-lg font-semibold text-slate-900 dark:text-white mb-4">
          Incoming Orders {filteredOrders.length !== displayOrders.length && `(${filteredOrders.length})`}
        </h2>
        {isLoading ? (
          <p className="text-sm text-slate-600 dark:text-slate-400" role="status">Loading orders…</p>
        ) : (
          <>
            {actionError && (
              <p role="alert" className="mb-3 text-sm text-red-700 dark:text-red-400">{actionError}</p>
            )}
            <IncomingOrdersTable orders={filteredOrders} onStatusChange={handleStatusChange} />
          </>
        )}
      </section>
    </div>
  );
}
