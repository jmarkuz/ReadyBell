import { useEffect, useMemo, useRef, useState, useCallback, type ChangeEvent } from 'react';
import OrderMetricsGrid from '@/components/OrderMetricsGrid';
import IncomingOrdersTable, { type SortField, type SortState } from '@/components/IncomingOrdersTable';
import StatusFilter from '@/components/StatusFilter';
import { DateRangeFilter } from '@/components/DateRangeFilter';
import { useSearch } from '@/context/SearchContext';
import { OrderMetrics, OrderStatus } from '@/types';
import { useDashboardOrders, type OrdersQuery, type OrdersSortBy } from '@/hooks/useDashboardOrders';
import { apiClient, ApiError } from '@/utils/api';
import { parseTestDataFile } from '@/utils/testDataFile';
import NewOrderDialog from '@/components/NewOrderDialog';
import { Plus } from 'lucide-react';

/** Incoming Orders rows per page. */
const PAGE_SIZE = 10;

/** Table column → the backend's sortBy value; Items has no server-side sort. */
const SORT_BY: Record<SortField, OrdersSortBy | null> = {
  id: 'orderId',
  customerName: 'customerName',
  deliveryETA: 'eta',
  status: 'status',
  itemsOrdered: null,
};

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
 * - Search, status filter, date range (by creation time), sorting and paging all run on the server
 *   (GET /api/orders); the table shows one page of 10 and any filter or sort change goes back to page 1
 * - Status filter: select multiple statuses (CREATED, PREPARING, READY, COMPLETED)
 * - The current page is reloaded on SSE events (polling while offline)
 * - Metric cards show the restaurant's real totals from GET /api/orders/metrics, independent of filters
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

  // Sorting: null means the server's default order (newest first)
  const [sort, setSort] = useState<SortState | null>(null);

  // The page is remembered together with the filters it belongs to; once the filters or the sort
  // change, the stored page is stale and we are on page 1, without an extra render or request.
  const filterKey = JSON.stringify([
    selectedStatuses, searchQuery.trim(), dateRangeStart?.getTime() ?? null, dateRangeEnd?.getTime() ?? null, sort,
  ]);
  const [pageState, setPageState] = useState({ page: 1, filterKey });
  const page = pageState.filterKey === filterKey ? pageState.page : 1;
  const handlePageChange = useCallback((next: number) => setPageState({ page: next, filterKey }), [filterKey]);

  const query = useMemo<OrdersQuery>(() => ({
    page,
    pageSize: PAGE_SIZE,
    statuses: selectedStatuses,
    search: searchQuery,
    from: dateRangeStart,
    to: dateRangeEnd,
    sortBy: (sort && SORT_BY[sort.field]) || 'created',
    sortOrder: sort ? sort.order : 'desc',
  }), [page, selectedStatuses, searchQuery, dateRangeStart, dateRangeEnd, sort]);

  // Orders come from the backend (GET /api/orders, one page) and are refreshed on SSE events
  const {
    orders: pageOrders, totalCount, pageCount, metrics: serverMetrics, isLoading, error: loadError, isConnected, reload,
  } = useDashboardOrders(query);

  // The page vanished under us (orders deleted elsewhere): step back to the last one that exists
  useEffect(() => {
    const last = Math.max(1, pageCount);
    if (!isLoading && page > last) handlePageChange(last);
  }, [isLoading, page, pageCount, handlePageChange]);
  const [actionError, setActionError] = useState<string | null>(null);

  const [newOrderOpen, setNewOrderOpen] = useState(false);
  const closeNewOrder = useCallback(() => setNewOrderOpen(false), []);

  // Dev-only test data tools (the backend endpoints exist under the `dev` profile only)
  const [testDataBusy, setTestDataBusy] = useState(false);
  const [testDataMessage, setTestDataMessage] = useState<string | null>(null);

  const runTestData = useCallback(async (action: () => Promise<string>) => {
    setTestDataBusy(true);
    setTestDataMessage(null);
    try {
      setTestDataMessage(await action());
    } catch (e) {
      setTestDataMessage(
        e instanceof ApiError && e.statusCode === 404
          ? 'Test data endpoints not found: restart the backend with SPRING_PROFILES_ACTIVE=dev.'
          : e instanceof Error ? e.message : 'Test data request failed.'
      );
    } finally {
      setTestDataBusy(false);
      await reload();
    }
  }, [reload]);

  const handleGenerateTestData = useCallback(
    () => runTestData(async () => `Added ${(await apiClient.generateTestData()).created} test orders.`),
    [runTestData]
  );
  const importInput = useRef<HTMLInputElement>(null);
  const handleImportFile = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      e.target.value = ''; // allow importing the same file again
      if (!file) return;
      void runTestData(async () => {
        const orders = parseTestDataFile(await file.text());
        return `Imported ${(await apiClient.importTestData(orders)).created} test orders from ${file.name}.`;
      });
    },
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


  // Real totals from the server; zeros until the first response
  const metrics: OrderMetrics = useMemo(() => serverMetrics ?? {
    totalOrders: 0, newOrders: 0, preparing: 0, readyForPickup: 0, completed: 0, cancelled: 0,
  }, [serverMetrics]);

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
        
        <button
          type="button"
          onClick={() => setNewOrderOpen(true)}
          className="inline-flex items-center gap-2 h-10 px-5 whitespace-nowrap rounded-full bg-stone-800 text-white dark:bg-stone-100 dark:text-stone-900 text-sm font-medium hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          <Plus className="w-4 h-4" aria-hidden="true" />
          New order
        </button>

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

      <NewOrderDialog open={newOrderOpen} onClose={closeNewOrder} onCreated={reload} />

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
            onClick={() => importInput.current?.click()}
            disabled={testDataBusy}
            className="px-4 py-2 text-sm font-medium border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-slate-200 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-60 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
          >
            Import test data
          </button>
          <input
            ref={importInput}
            type="file"
            accept=".json,application/json"
            onChange={handleImportFile}
            className="hidden"
            aria-label="Test data file"
            data-testid="test-data-file"
          />
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
            Search results: <strong>{totalCount}</strong> order(s) found for "{searchQuery}"
          </p>
        </div>
      )}

      {/* Incoming Orders Table */}
      <section aria-labelledby="orders-heading">
        <h2 id="orders-heading" className="text-lg font-semibold text-slate-900 dark:text-white mb-4">
          Incoming Orders {(searchQuery.trim() || activeFilterCount > 0) && `(${totalCount})`}
        </h2>
        {isLoading ? (
          <p className="text-sm text-slate-600 dark:text-slate-400" role="status">Loading orders…</p>
        ) : (
          <>
            {actionError && (
              <p role="alert" className="mb-3 text-sm text-red-700 dark:text-red-400">{actionError}</p>
            )}
            <IncomingOrdersTable
              orders={pageOrders}
              pageSize={PAGE_SIZE}
              currentPage={page}
              onPageChange={handlePageChange}
              onStatusChange={handleStatusChange}
              serverSide={{ totalCount, sort, onSortChange: setSort }}
            />
          </>
        )}
      </section>
    </div>
  );
}
