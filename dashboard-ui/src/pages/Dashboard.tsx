import { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import OrderMetricsGrid from '@/components/OrderMetricsGrid';
import IncomingOrdersTable, { type SortField, type SortState } from '@/components/IncomingOrdersTable';
import StatusFilter from '@/components/StatusFilter';
import { DateRangeFilter } from '@/components/DateRangeFilter';
import { useSearch } from '@/context/SearchContext';
import { OrderMetrics, OrderStatus } from '@/types';
import { useDashboardOrders, type NewOrderEvent, type OrdersQuery, type OrdersSortBy } from '@/hooks/useDashboardOrders';
import { useNewOrderAlerts } from '@/context/alertContext';
import { apiClient } from '@/utils/api';
import NewOrderDialog from '@/components/NewOrderDialog';
import OrderQrDialog from '@/components/OrderQrDialog';
import { useRestaurant } from '@/context/restaurantContext';
import { Link } from 'react-router-dom';
import { MonitorSmartphone, Plus } from 'lucide-react';

/** Incoming Orders rows per page. */
const PAGE_SIZE = 10;

/** How long a "new order" alert waits to learn whether the order was entered on this screen. */
const OWN_ORDER_GRACE_MS = 1500;

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

  // An order staff entered on this very screen must not beep at them. The server's "new order" event can arrive
  // before the response to our own request, so the alert waits a moment and checks whether the order is ours.
  const { notifyNewOrder } = useNewOrderAlerts();
  const ownOrderIds = useRef(new Set<string>());
  const alertTimers = useRef(new Set<ReturnType<typeof setTimeout>>());
  const handleNewOrder = useCallback((order: NewOrderEvent) => {
    const timer = setTimeout(() => {
      alertTimers.current.delete(timer);
      if (!ownOrderIds.current.has(order.id)) notifyNewOrder(order.orderNumber);
    }, OWN_ORDER_GRACE_MS);
    alertTimers.current.add(timer);
  }, [notifyNewOrder]);
  useEffect(() => {
    const timers = alertTimers.current;
    return () => timers.forEach(clearTimeout);
  }, []);

  // Orders whose customer has opened the tracking link since this screen loaded (the list carries the rest)
  const [connectedIds, setConnectedIds] = useState<ReadonlySet<string>>(() => new Set());
  const handleOrderConnected = useCallback((orderId: string) => {
    setConnectedIds((prev) => new Set(prev).add(orderId));
  }, []);

  // Orders come from the backend (GET /api/orders, one page) and are refreshed on SSE events
  const {
    orders: pageOrders, totalCount, pageCount, metrics: serverMetrics, isLoading, error: loadError, isConnected, reload,
  } = useDashboardOrders(query, { onNewOrder: handleNewOrder, onOrderConnected: handleOrderConnected });

  // The page vanished under us (orders deleted elsewhere): step back to the last one that exists
  useEffect(() => {
    const last = Math.max(1, pageCount);
    if (!isLoading && page > last) handlePageChange(last);
  }, [isLoading, page, pageCount, handlePageChange]);
  const [actionError, setActionError] = useState<string | null>(null);

  const restaurant = useRestaurant();
  const [qrOrderId, setQrOrderId] = useState<string | null>(null);
  const closeQr = useCallback(() => setQrOrderId(null), []);
  const qrOrder = qrOrderId ? pageOrders.find((o) => o.id === qrOrderId) : undefined;

  const [newOrderOpen, setNewOrderOpen] = useState(false);
  const closeNewOrder = useCallback(() => setNewOrderOpen(false), []);
  const handleOrderCreated = useCallback((orderId: string) => {
    ownOrderIds.current.add(orderId);
    void reload();
  }, [reload]);

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
        
        <Link
          to="/display"
          target="_blank"
          className="inline-flex items-center gap-2 h-10 px-4 whitespace-nowrap rounded-full border border-slate-300 dark:border-slate-600 text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          <MonitorSmartphone className="w-4 h-4" aria-hidden="true" />
          Customer display
        </Link>

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

      <NewOrderDialog open={newOrderOpen} onClose={closeNewOrder} onCreated={handleOrderCreated} connectedOrderIds={connectedIds} />
      {qrOrder?.sessionId && (
        <OrderQrDialog
          order={{ orderNumber: qrOrder.orderId, sessionId: qrOrder.sessionId }}
          connected={Boolean(qrOrder.connected) || connectedIds.has(qrOrder.id)}
          restaurantName={restaurant.name}
          onClose={closeQr}
        />
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
              onRowClick={setQrOrderId}
              connectedIds={connectedIds}
              serverSide={{ totalCount, sort, onSortChange: setSort }}
            />
          </>
        )}
      </section>
    </div>
  );
}
