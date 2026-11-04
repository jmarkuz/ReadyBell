/**
 * Mock Data for Testing
 * Provides sample order data and other mock objects for unit/integration tests
 */

import { OrderRow, OrderStatus } from '@/types';

/**
 * Generate mock orders with varied statuses
 * Used in tests to simulate real order data
 */
export function generateMockOrders(count: number = 25): OrderRow[] {
  const customerNames = ['John Smith', 'Sarah Johnson', 'Mike Chen', 'Emily Brown', 'David Lee', 'Lisa Garcia'];
  const items = ['Burger Combo', 'Pizza Margherita', 'Caesar Salad', 'Fish & Chips', 'Pasta Carbonara', 'Chicken Sandwich'];

  const orders: OrderRow[] = [];

  for (let i = 0; i < count; i++) {
    const now = new Date();
    const eta = new Date(now.getTime() + Math.random() * 30 * 60000); // 0-30 minutes from now

    // Distribute statuses: 5-10 CREATED, 8-12 PREPARING, 5-8 READY, 3-5 COMPLETED, 1-2 CANCELLED
    let status: OrderStatus;
    const rand = Math.random();
    if (rand < 0.25) status = 'CREATED';        // 25% new
    else if (rand < 0.55) status = 'PREPARING';  // 30% preparing
    else if (rand < 0.75) status = 'READY';      // 20% ready
    else if (rand < 0.92) status = 'COMPLETED';  // 17% completed
    else status = 'CANCELLED';                    // 8% cancelled

    const estimatedWaitTime = Math.max(0, Math.floor((eta.getTime() - now.getTime()) / 60000));

    orders.push({
      id: `order-${i + 1}`,
      orderId: `#${10000 + i}`,
      customerName: customerNames[Math.floor(Math.random() * customerNames.length)],
      itemsOrdered: items
        .sort(() => Math.random() - 0.5)
        .slice(0, Math.floor(Math.random() * 3) + 1)
        .join(', '),
      deliveryETA: eta,
      status,
      estimatedWaitTime,
      itemCount: Math.floor(Math.random() * 5) + 1,
    });
  }

  return orders;
}

/**
 * Default mock order data used in tests
 */
export const mockOrderData = generateMockOrders(15);

/**
 * Sample single order for detailed view testing
 */
export const mockSingleOrder: OrderRow = {
  id: 'order-1',
  orderId: '#10001',
  customerName: 'John Smith',
  itemsOrdered: 'Burger Combo, French Fries',
  deliveryETA: new Date(Date.now() + 15 * 60000), // 15 minutes from now
  status: 'PREPARING',
  estimatedWaitTime: 15,
  itemCount: 2,
};

/**
 * Mock user profile for sidebar testing
 */
export const mockUserProfile = {
  name: 'Alex Johnson',
  email: 'alex@readybell.com',
  role: 'Manager',
  restaurantName: 'Downtown Pizza Co.',
  avatarUrl: undefined, // Will show initials instead
};

/**
 * Mock menu items for filter testing
 */
export const mockMenuItems = [
  { id: '1', name: 'Burger Combo', price: 12.99 },
  { id: '2', name: 'Pizza Margherita', price: 14.99 },
  { id: '3', name: 'Caesar Salad', price: 9.99 },
  { id: '4', name: 'Fish & Chips', price: 11.99 },
  { id: '5', name: 'Pasta Carbonara', price: 13.99 },
];

/**
 * Mock notification data
 */
export const mockNotifications = [
  { id: '1', message: 'New order received: #10001', timestamp: new Date(), read: false },
  { id: '2', message: 'Order #10002 ready for pickup', timestamp: new Date(Date.now() - 5 * 60000), read: true },
  { id: '3', message: 'Order #10003 cancelled by customer', timestamp: new Date(Date.now() - 10 * 60000), read: true },
];
