import React, { useMemo } from 'react';
import OrderMetricsGrid from '@/components/OrderMetricsGrid';
import IncomingOrdersTable from '@/components/IncomingOrdersTable';
import { useSearch } from '@/context/SearchContext';
import { OrderMetrics, OrderRow } from '@/types';

/**
 * Dashboard Page
 * 
 * Main dashboard view with:
 * - Order summary cards grid (4 cards: Total, New, Preparing, Ready)
 * - Incoming orders table with search filtering
 * - Search integration with header
 * 
 * Features:
 * - Search filters by orderId, customerName, or itemsOrdered (case-insensitive, partial match)
 * - Metrics calculated from all orders (not filtered)
 * - Table displays filtered orders based on search
 * 
 * Responsive:
 * - Desktop (1024px+): 4-column card grid
 * - Tablet (768px-1023px): 2-column card grid
 * - Mobile (<768px): 1-column stacked
 */
export default function Dashboard() {
  // Get search query from context
  const { query: searchQuery } = useSearch();

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
  ]);

  // Filter orders based on search query
  const filteredOrders = useMemo(() => {
    if (!searchQuery.trim()) {
      return allOrders;
    }

    const query = searchQuery.toLowerCase();
    return allOrders.filter((order) => {
      return (
        order.orderId.toLowerCase().includes(query) ||
        (order.customerName?.toLowerCase().includes(query) ?? false) ||
        order.itemsOrdered.toLowerCase().includes(query)
      );
    });
  }, [allOrders, searchQuery]);

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
      <section>
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white mb-4">Order Summary</h2>
        <OrderMetricsGrid metrics={metrics} />
      </section>

      {/* Search Results Info */}
      {searchQuery && (
        <div className="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
          <p className="text-sm text-blue-700 dark:text-blue-300">
            Search results: <strong>{filteredOrders.length}</strong> order(s) found for "{searchQuery}"
          </p>
        </div>
      )}

      {/* Incoming Orders Table */}
      <section>
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white mb-4">
          Incoming Orders {searchQuery && `(${filteredOrders.length})`}
        </h2>
        <IncomingOrdersTable orders={filteredOrders} />
      </section>
    </div>
  );
}
