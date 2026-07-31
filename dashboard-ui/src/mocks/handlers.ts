/**
 * MSW (Mock Service Worker) Handlers
 * Provides mock API endpoints for development without running backend
 * 
 * Mirrors backend API contracts from ALIGNMENT.md
 */

import { http, HttpResponse } from 'msw';
import { OrderRow, PaginatedResponse } from '@/types';

/**
 * Generate mock orders with varied statuses
 */
function generateMockOrders(count: number = 25): OrderRow[] {
  const statuses = ['CREATED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED'] as const;
  const customerNames = ['John Smith', 'Sarah Johnson', 'Mike Chen', 'Emily Brown', 'David Lee', 'Lisa Garcia'];
  const items = ['Burger Combo', 'Pizza Margherita', 'Caesar Salad', 'Fish & Chips', 'Pasta Carbonara', 'Chicken Sandwich'];

  const orders: OrderRow[] = [];

  for (let i = 0; i < count; i++) {
    const now = new Date();
    // createdAgo is calculated but not used in this mock - could be used for more realistic timestamps
    // const createdAgo = Math.floor(Math.random() * 60); // 0-60 minutes ago
    const eta = new Date(now.getTime() + Math.random() * 30 * 60000); // 0-30 minutes from now

    // Distribute statuses: 5-10 CREATED, 8-12 PREPARING, 5-8 READY, 3-5 COMPLETED, 1-2 CANCELLED
    let status: typeof statuses[number];
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
 * MSW Handlers for backend API endpoints
 * These are defined according to backend contracts in ALIGNMENT.md
 */
export const handlers = [
  /**
   * GET /api/orders - List orders with pagination and filtering
   * Query params: page, pageSize, status[], sortBy, sortOrder, search
   * Response: PaginatedResponse<OrderRow>
   */
  http.get('/api/orders', ({ request }) => {
    const url = new URL(request.url);
    const page = parseInt(url.searchParams.get('page') || '1');
    const pageSize = parseInt(url.searchParams.get('pageSize') || '25');
    const statusFilter = url.searchParams.getAll('status');
    const sortBy = url.searchParams.get('sortBy') || 'created';
    const sortOrder = url.searchParams.get('sortOrder') || 'desc';
    const search = url.searchParams.get('search')?.toLowerCase();

    // Generate mock data
    const allOrders = generateMockOrders(100);

    // Apply status filter
    let filtered = allOrders;
    if (statusFilter.length > 0) {
      filtered = allOrders.filter(order => statusFilter.includes(order.status));
    }

    // Apply search filter
    if (search) {
      filtered = filtered.filter(
        order =>
          order.orderId.toLowerCase().includes(search) ||
          order.customerName?.toLowerCase().includes(search) ||
          order.itemsOrdered.toLowerCase().includes(search)
      );
    }

    // Apply sorting
    const sorted = [...filtered].sort((a, b) => {
      let aVal: any, bVal: any;

      switch (sortBy) {
        case 'eta':
          aVal = a.deliveryETA.getTime();
          bVal = b.deliveryETA.getTime();
          break;
        case 'status':
          aVal = a.status;
          bVal = b.status;
          break;
        case 'created':
        default:
          aVal = a.deliveryETA.getTime(); // Using ETA as proxy for created time
          bVal = b.deliveryETA.getTime();
      }

      const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
      return sortOrder === 'asc' ? comparison : -comparison;
    });

    // Apply pagination
    const totalCount = sorted.length;
    const pageCount = Math.ceil(totalCount / pageSize);
    const start = (page - 1) * pageSize;
    const paginatedOrders = sorted.slice(start, start + pageSize);

    return HttpResponse.json<PaginatedResponse<OrderRow>>(
      {
        items: paginatedOrders,
        totalCount,
        pageCount,
        currentPage: page,
      },
      { status: 200 }
    );
  }),

  /**
   * POST /api/orders - Create a new order
   * Request: { items: Array<{menuItemId, quantity}>, specialInstructions? }
   * Response: { id: string, orderId: string, status: 'CREATED', createdAt: Date }
   */
  http.post('/api/orders', async ({ request }) => {
    try {
      // Note: body is available but not validated in this mock
      // In real implementation, would validate body structure
      await request.json();

      return HttpResponse.json(
        {
          id: `order-${Math.random().toString(36).substr(2, 9)}`,
          orderId: `#${Math.floor(Math.random() * 100000)}`,
          status: 'CREATED',
          createdAt: new Date().toISOString(),
        },
        { status: 201 }
      );
    } catch (error) {
      return HttpResponse.json(
        {
          type: 'urn:readybell:orders:invalid-request',
          title: 'Invalid Request',
          status: 400,
          detail: 'Request body is invalid',
        },
        { status: 400 }
      );
    }
  }),

  /**
   * PATCH /api/orders/{id}/status - Update order status
   * Request: { status: 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED' }
   * Response: { id, status, updatedAt }
   */
  http.patch('/api/orders/:orderId/status', async ({ params, request }) => {
    try {
      const body = await request.json() as { status: string };
      const validStatuses = ['PREPARING', 'READY', 'COMPLETED', 'CANCELLED'];

      if (!validStatuses.includes(body.status)) {
        return HttpResponse.json(
          {
            type: 'urn:readybell:orders:invalid-transition',
            title: 'Invalid State Transition',
            status: 400,
            detail: `Invalid status: ${body.status}`,
          },
          { status: 400 }
        );
      }

      return HttpResponse.json(
        {
          id: params.orderId,
          status: body.status,
          updatedAt: new Date().toISOString(),
        },
        { status: 200 }
      );
    } catch (error) {
      return HttpResponse.json(
        {
          type: 'urn:readybell:orders:invalid-request',
          title: 'Invalid Request',
          status: 400,
          detail: 'Request body is invalid',
        },
        { status: 400 }
      );
    }
  }),

  /**
   * GET /api/orders/stream - SSE stream for real-time order updates
   * Headers: Authorization: Bearer {token}
   * Response: text/event-stream
   * Events: OrderCreated, OrderStatusUpdated
   */
  http.get('/api/orders/stream', () => {
    // Create a simple mock SSE stream
    // In production, this would be a real SSE connection
    const readableStream = new ReadableStream({
      start(controller) {
        // Send an initial message
        controller.enqueue('data: {"type": "connection_established"}\n\n');

        // Simulate order updates every 3 seconds
        const interval = setInterval(() => {
          const event = Math.random() > 0.5 ? 'OrderStatusUpdated' : 'OrderCreated';
          const data = {
            orderId: `#${Math.floor(Math.random() * 100000)}`,
            status: ['CREATED', 'PREPARING', 'READY', 'COMPLETED'][Math.floor(Math.random() * 4)],
            updatedAt: new Date().toISOString(),
          };
          controller.enqueue(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
        }, 3000);

        // Cleanup interval on stream close
        return () => clearInterval(interval);
      },
    });

    return new HttpResponse(readableStream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      },
      status: 200,
    });
  }),

  /**
   * GET /api/orders/poll - Polling fallback for order updates
   * Query params: since?, eTag?
   * Response: { orders: OrderRow[], eTag: string }
   * Headers: ETag: {eTag}
   */
  http.get('/api/orders/poll', ({ request }) => {
    const url = new URL(request.url);
    const eTag = url.searchParams.get('eTag');
    // since parameter available for conditional updates but not used in this simple mock
    // const since = url.searchParams.get('since');

    // Generate mock orders
    const orders = generateMockOrders(25);

    // Simulate ETag conditional response
    const currentETag = `"${Math.random().toString(36).substr(2, 9)}"`;
    if (eTag && eTag === currentETag) {
      // Return 304 Not Modified
      return new HttpResponse(null, {
        status: 304,
        headers: { 'ETag': currentETag },
      });
    }

    return HttpResponse.json(
      { orders, eTag: currentETag },
      {
        headers: { 'ETag': currentETag },
        status: 200,
      }
    );
  }),

  /**
   * Error scenario handlers for testing error states
   */

  /**
   * GET /api/orders/error/network - Simulate network timeout
   */
  http.get('/api/orders/error/network', () => {
    return HttpResponse.error();
  }),

  /**
   * GET /api/orders/error/401 - Simulate auth failure
   */
  http.get('/api/orders/error/401', () => {
    return HttpResponse.json(
      {
        type: 'urn:readybell:auth:invalid-token',
        title: 'Unauthorized',
        status: 401,
        detail: 'Invalid or expired token',
      },
      { status: 401 }
    );
  }),

  /**
   * GET /api/orders/error/500 - Simulate server error
   */
  http.get('/api/orders/error/500', () => {
    return HttpResponse.json(
      {
        type: 'urn:readybell:orders:server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: 'An unexpected error occurred',
      },
      { status: 500 }
    );
  }),
];
