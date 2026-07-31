import React, { useMemo } from 'react';
import OrderMetricsGrid from '@/components/OrderMetricsGrid';
import IncomingOrdersTable from '@/components/IncomingOrdersTable';
import { OrderMetrics, OrderRow } from '@/types';

/**
 * Dashboard Page
 * 
 * Main dashboard view with:
 * - Order summary cards grid (4 cards: Total, New, Preparing, Ready)
 * - Incoming orders table (placeholder)
 * 
 * Responsive:
 * - Desktop (1024px+): 4-column card grid
 * - Tablet (768px-1023px): 2-column card grid
 * - Mobile (<768px): 1-column stacked
 */
export default function Dashboard() {
  // Mock orders data - will be replaced with API call
  const [orders] = React.useState<OrderRow[]>([
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

  // Calculate metrics from orders
  const metrics: OrderMetrics = useMemo(() => {
    return {
      totalOrders: orders.length,
      newOrders: orders.filter((o) => o.status === 'CREATED').length,
      preparing: orders.filter((o) => o.status === 'PREPARING').length,
      readyForPickup: orders.filter((o) => o.status === 'READY').length,
      completed: orders.filter((o) => o.status === 'COMPLETED').length,
      cancelled: orders.filter((o) => o.status === 'CANCELLED').length,
    };
  }, [orders]);

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

      {/* Incoming Orders Table */}
      <section>
        <h2 className="text-lg font-semibold text-slate-900 dark:text-white mb-4">Incoming Orders</h2>
        <IncomingOrdersTable orders={orders} />
      </section>
    </div>
  );
}
