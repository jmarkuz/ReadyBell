import React, { useMemo, useState } from 'react';
import OrderMetricsGrid from '@/components/OrderMetricsGrid';
import IncomingOrdersTable from '@/components/IncomingOrdersTable';
import StatusFilter from '@/components/StatusFilter';
import { DateRangeFilter } from '@/components/DateRangeFilter';
import { useSearch } from '@/context/SearchContext';
import { OrderMetrics, OrderRow, OrderStatus } from '@/types';

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
 * - Metrics calculated from all orders (not filtered)
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

  // Mock orders data - will be replaced with API call
  const [allOrders] = React.useState<OrderRow[]>([
    {
      id: '1',
      orderId: 'ORD-001',
      customerName: 'John Smith',
      itemsOrdered: 'Burger, Fries, Drink',
      deliveryETA: new Date(Date.now() + 15 * 60000), // 15 minutes from now
      status: 'PREPARING',
      estimatedWaitTime: 15,
      itemCount: 3,
    },
    {
      id: '2',
      orderId: 'ORD-002',
      customerName: 'Jane Doe',
      itemsOrdered: 'Pizza, Salad',
      deliveryETA: new Date(Date.now() + 25 * 60000), // 25 minutes from now
      status: 'CREATED',
      estimatedWaitTime: 25,
      itemCount: 2,
    },
    {
      id: '3',
      orderId: 'ORD-003',
      customerName: 'Bob Johnson',
      itemsOrdered: 'Sandwich, Chips',
      deliveryETA: new Date(Date.now() + 5 * 60000), // 5 minutes from now
      status: 'READY',
      estimatedWaitTime: 5,
      itemCount: 2,
    },
    {
      id: '4',
      orderId: 'ORD-004',
      customerName: 'Alice Brown',
      itemsOrdered: 'Tacos, Rice, Beans',
      deliveryETA: new Date(Date.now() + 35 * 60000), // 35 minutes from now
      status: 'COMPLETED',
      estimatedWaitTime: 35,
      itemCount: 3,
    },
    {
      id: '5',
      orderId: 'ORD-005',
      customerName: 'Charlie Wilson',
      itemsOrdered: 'Pasta, Sauce',
      deliveryETA: new Date(Date.now() + 12 * 60000), // 12 minutes from now
      status: 'PREPARING',
      estimatedWaitTime: 12,
      itemCount: 2,
    },
  ]);

  // Filter orders based on search query, status, and date range
  const filteredOrders = useMemo(() => {
    let result = [...allOrders];

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
        return orderDate >= dateRangeStart && orderDate <= dateRangeEnd;
      });
    }

    return result;
  }, [allOrders, searchQuery, selectedStatuses, dateRangeStart, dateRangeEnd]);

  // Calculate metrics from ALL orders (not filtered)
  const metrics: OrderMetrics = useMemo(() => {
    return {
      totalOrders: allOrders.length,
      newOrders: allOrders.filter((o) => o.status === 'CREATED').length,
      preparing: allOrders.filter((o) => o.status === 'PREPARING').length,
      readyForPickup: allOrders.filter((o) => o.status === 'READY').length,
      completed: allOrders.filter((o) => o.status === 'COMPLETED').length,
      cancelled: allOrders.filter((o) => o.status === 'CANCELLED').length,
    };
  }, [allOrders]);

  // Count active filters
  const activeFilterCount = (selectedStatuses.length > 0 ? 1 : 0) + (dateRangeStart && dateRangeEnd ? 1 : 0);

  // Handle status filter apply
  const handleStatusFilterApply = (statuses: OrderStatus[]) => {
    setSelectedStatuses(statuses);
  };

  // Handle date range filter apply
  const handleDateRangeApply = (startDate: Date, endDate: Date) => {
    setDateRangeStart(startDate);
    setDateRangeEnd(endDate);
  };

  // Handle clear all filters
  const handleClearAllFilters = () => {
    setSelectedStatuses([]);
    setDateRangeStart(null);
    setDateRangeEnd(null);
    setShowDateFilter(false);
  };

  return (
    <div className="space-y-8">
      {/* Page title */}
      <div>
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white">Dashboard</h1>
        <p className="text-slate-600 dark:text-slate-400 mt-2">
          Welcome to the ReadyBell restaurant dashboard. Monitor incoming orders and track operations.
        </p>
      </div>

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
              className="flex items-center gap-2 px-3 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors relative focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
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
                className="px-3 py-2 text-sm font-medium text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
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
          Incoming Orders {filteredOrders.length !== allOrders.length && `(${filteredOrders.length})`}
        </h2>
        <IncomingOrdersTable orders={filteredOrders} />
      </section>
    </div>
  );
}
