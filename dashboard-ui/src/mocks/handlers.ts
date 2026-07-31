/**
 * MSW (Mock Service Worker) Handlers
 * Provides mock API endpoints for development without running backend
 * 
 * Mirrors backend API contracts from BACKEND_ENDPOINTS_INTEGRATION.md
 * Implements restaurant-notification-platform spec endpoints
 */

import { http, HttpResponse } from 'msw';
import { OrderRow, PaginatedResponse } from '@/types';

/**
 * Mock authentication token validation
 */
function validateAuthToken(request: Request): { valid: boolean; tenantId?: string; role?: string } {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader) return { valid: false };

  const tokenMatch = authHeader.match(/Bearer\s+(.+)/);
  if (!tokenMatch) return { valid: false };

  const token = tokenMatch[1];
  if (token === 'invalid-token' || token === '') return { valid: false };

  try {
    let role = 'STAFF';
    let tenantId = 'tenant-123';

    if (token.startsWith('admin-')) {
      role = 'ADMIN';
      tenantId = token.split('-')[1] || 'tenant-123';
    } else if (token.startsWith('platform-')) {
      role = 'PLATFORM_ADMIN';
    }

    return { valid: true, tenantId, role };
  } catch (error) {
    return { valid: false };
  }
}

/**
 * Generate mock orders scoped to tenant
 */
function generateMockOrders(count: number = 25, tenantId?: string): OrderRow[] {
  const statuses = ['CREATED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED'] as const;
  const customerNames = ['John Smith', 'Sarah Johnson', 'Mike Chen', 'Emily Brown', 'David Lee', 'Lisa Garcia'];
  const items = ['Burger Combo', 'Pizza Margherita', 'Caesar Salad', 'Fish & Chips', 'Pasta Carbonara'];

  const orders: OrderRow[] = [];
  const now = new Date();

  for (let i = 0; i < count; i++) {
    const createdAtOffset = Math.floor(Math.random() * 120 * 60000);
    const rand = Math.random();
    
    let status: typeof statuses[number];
    let eta: Date;
    
    if (rand < 0.20) {
      status = 'CREATED';
      eta = new Date(now.getTime() + 15 * 60000);
    } else if (rand < 0.50) {
      status = 'PREPARING';
      eta = new Date(now.getTime() + 10 * 60000);
    } else if (rand < 0.70) {
      status = 'READY';
      eta = new Date(now.getTime() + 1 * 60000);
    } else if (rand < 0.90) {
      status = 'COMPLETED';
      eta = new Date(now.getTime() - 5 * 60000);
    } else {
      status = 'CANCELLED';
      eta = new Date(now.getTime() - createdAtOffset);
    }

    const estimatedWaitTime = Math.max(0, Math.floor((eta.getTime() - now.getTime()) / 60000));

    orders.push({
      id: `order-${tenantId || 'default'}-${i + 1}`,
      orderId: `#${10000 + i}`,
      customerName: customerNames[Math.floor(Math.random() * customerNames.length)],
      itemsOrdered: items.slice(0, Math.floor(Math.random() * 3) + 1).join(', '),
      deliveryETA: eta,
      status,
      estimatedWaitTime,
      itemCount: Math.floor(Math.random() * 5) + 1,
    });
  }

  return orders;
}

/**
 * MSW Handlers - Restaurant Notification Platform Endpoints
 */
export const handlers = [
  // POST /api/login - Authentication
  http.post('/api/login', async ({ request }) => {
    try {
      const body = (await request.json()) as { username?: string; password?: string };
      
      if (!body.username || !body.password) {
        return HttpResponse.json(
          {
            type: 'urn:readybell:auth:invalid-credentials',
            title: 'Invalid Credentials',
            status: 401,
            detail: 'Username and password required',
          },
          { status: 401 }
        );
      }

      // Mock credentials
      if (body.username === 'staff@restaurant.com' && body.password === 'password') {
        return HttpResponse.json(
          {
            token: 'staff-tenant-123-abc123',
            role: 'STAFF',
            tenantId: 'tenant-123',
            username: body.username,
            expiresIn: 3600,
          },
          { status: 200 }
        );
      }

      if (body.username === 'admin@restaurant.com' && body.password === 'password') {
        return HttpResponse.json(
          {
            token: 'admin-tenant-123-def456',
            role: 'ADMIN',
            tenantId: 'tenant-123',
            username: body.username,
            expiresIn: 3600,
          },
          { status: 200 }
        );
      }

      return HttpResponse.json(
        {
          type: 'urn:readybell:auth:invalid-credentials',
          title: 'Invalid Credentials',
          status: 401,
          detail: 'Invalid username or password',
        },
        { status: 401 }
      );
    } catch (error) {
      return HttpResponse.json(
        {
          type: 'urn:readybell:validation:invalid-request',
          title: 'Invalid Request',
          status: 400,
          detail: 'Request body is invalid',
        },
        { status: 400 }
      );
    }
  }),

  // GET /api/orders - List orders with authentication
  http.get('/api/orders', ({ request }) => {
    const auth = validateAuthToken(request);
    if (!auth.valid) {
      return HttpResponse.json(
        {
          type: 'urn:readybell:auth:invalid-token',
          title: 'Unauthorized',
          status: 401,
          detail: 'Invalid or expired token',
        },
        { status: 401 }
      );
    }

    const url = new URL(request.url);
    const page = parseInt(url.searchParams.get('page') || '1');
    const pageSize = parseInt(url.searchParams.get('pageSize') || '25');
    const statusFilter = url.searchParams.getAll('status');
    const sortBy = url.searchParams.get('sortBy') || 'created';
    const sortOrder = url.searchParams.get('sortOrder') || 'desc';
    const search = url.searchParams.get('search')?.toLowerCase();

    if (page < 1 || pageSize < 1 || pageSize > 100) {
      return HttpResponse.json(
        {
          type: 'urn:readybell:validation:invalid-filter',
          title: 'Invalid Filter',
          status: 400,
          detail: 'Page must be >= 1, pageSize must be 1-100',
        },
        { status: 400 }
      );
    }

    let allOrders = generateMockOrders(100, auth.tenantId);

    // Apply filters
    if (statusFilter.length > 0) {
      const validStatuses = ['CREATED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED'];
      const invalidStatuses = statusFilter.filter(s => !validStatuses.includes(s));
      if (invalidStatuses.length > 0) {
        return HttpResponse.json(
          {
            type: 'urn:readybell:validation:invalid-filter',
            title: 'Invalid Filter',
            status: 400,
            detail: `Invalid status values: ${invalidStatuses.join(', ')}`,
          },
          { status: 400 }
        );
      }
      allOrders = allOrders.filter(order => statusFilter.includes(order.status));
    }

    if (search) {
      allOrders = allOrders.filter(
        order =>
          order.orderId.toLowerCase().includes(search) ||
          order.customerName?.toLowerCase().includes(search) ||
          order.itemsOrdered.toLowerCase().includes(search)
      );
    }

    // Apply sorting
    const sorted = [...allOrders].sort((a, b) => {
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
        case 'customerName':
          aVal = a.customerName?.toLowerCase();
          bVal = b.customerName?.toLowerCase();
          break;
        default:
          aVal = a.orderId;
          bVal = b.orderId;
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

  // POST /api/orders - Create order
  http.post('/api/orders', async ({ request }) => {
    const auth = validateAuthToken(request);
    if (!auth.valid) {
      return HttpResponse.json(
        { type: 'urn:readybell:auth:invalid-token', title: 'Unauthorized', status: 401, detail: 'Invalid token' },
        { status: 401 }
      );
    }

    try {
      const body = (await request.json()) as { customerSessionId?: string; items?: any[] };
      if (!body.customerSessionId) {
        return HttpResponse.json(
          {
            type: 'urn:readybell:validation:invalid-request',
            title: 'Invalid Request',
            status: 400,
            detail: 'customerSessionId is required',
          },
          { status: 400 }
        );
      }

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
        { type: 'urn:readybell:validation:invalid-request', title: 'Invalid Request', status: 400, detail: 'Invalid body' },
        { status: 400 }
      );
    }
  }),

  // PATCH /api/orders/{orderId}/status - Update status
  http.patch('/api/orders/:orderId/status', async ({ params, request }) => {
    const auth = validateAuthToken(request);
    if (!auth.valid) {
      return HttpResponse.json(
        { type: 'urn:readybell:auth:invalid-token', title: 'Unauthorized', status: 401, detail: 'Invalid token' },
        { status: 401 }
      );
    }

    try {
      const body = (await request.json()) as { status?: string };
      if (!body.status) {
        return HttpResponse.json(
          { type: 'urn:readybell:validation:invalid-request', title: 'Invalid Request', status: 400, detail: 'Status required' },
          { status: 400 }
        );
      }

      const validStatuses = ['PREPARING', 'READY', 'COMPLETED', 'CANCELLED'];
      if (!validStatuses.includes(body.status)) {
        return HttpResponse.json(
          {
            type: 'urn:readybell:orders:invalid-transition',
            title: 'Invalid Transition',
            status: 400,
            detail: `Invalid status: ${body.status}`,
          },
          { status: 400 }
        );
      }

      return HttpResponse.json(
        { id: params.orderId, status: body.status, updatedAt: new Date().toISOString() },
        { status: 200 }
      );
    } catch (error) {
      return HttpResponse.json(
        { type: 'urn:readybell:validation:invalid-request', title: 'Invalid Request', status: 400, detail: 'Invalid body' },
        { status: 400 }
      );
    }
  }),

  // GET /api/orders/stream - SSE stream
  http.get('/api/orders/stream', ({ request }) => {
    const auth = validateAuthToken(request);
    if (!auth.valid) {
      return HttpResponse.json(
        { type: 'urn:readybell:auth:invalid-token', title: 'Unauthorized', status: 401, detail: 'Invalid token' },
        { status: 401 }
      );
    }

    const readableStream = new ReadableStream({
      start(controller) {
        controller.enqueue('data: {"type": "connection_established"}\n\n');
        const interval = setInterval(() => {
          const eventType = Math.random() > 0.6 ? 'OrderStatusUpdated' : 'OrderCreated';
          const statuses = ['CREATED', 'PREPARING', 'READY', 'COMPLETED'];
          const data = {
            orderId: `#${Math.floor(Math.random() * 100000)}`,
            status: statuses[Math.floor(Math.random() * statuses.length)],
            updatedAt: new Date().toISOString(),
          };
          controller.enqueue(`event: ${eventType}\ndata: ${JSON.stringify(data)}\n\n`);
        }, 3000);
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

  // GET /api/orders/poll - Polling fallback
  http.get('/api/orders/poll', ({ request }) => {
    const auth = validateAuthToken(request);
    if (!auth.valid) {
      return HttpResponse.json(
        { type: 'urn:readybell:auth:invalid-token', title: 'Unauthorized', status: 401, detail: 'Invalid token' },
        { status: 401 }
      );
    }

    const url = new URL(request.url);
    const eTag = url.searchParams.get('eTag');
    const orders = generateMockOrders(25, auth.tenantId);
    const currentETag = `"${Math.random().toString(36).substr(2, 9)}"`;

    if (eTag && eTag === currentETag) {
      return new HttpResponse(null, { status: 304, headers: { 'ETag': currentETag } });
    }

    return HttpResponse.json({ orders, eTag: currentETag }, { headers: { 'ETag': currentETag }, status: 200 });
  }),

  // Error handlers
  http.get('/api/orders/error/network', () => HttpResponse.error()),
  http.get('/api/orders/error/401', () =>
    HttpResponse.json(
      { type: 'urn:readybell:auth:invalid-token', title: 'Unauthorized', status: 401, detail: 'Invalid token' },
      { status: 401 }
    )
  ),
  http.get('/api/orders/error/403', () =>
    HttpResponse.json(
      { type: 'urn:readybell:auth:tenant-mismatch', title: 'Forbidden', status: 403, detail: 'Different tenant' },
      { status: 403 }
    )
  ),
  http.get('/api/orders/error/500', () =>
    HttpResponse.json(
      { type: 'urn:readybell:orders:server-error', title: 'Server Error', status: 500, detail: 'Unexpected error' },
      { status: 500 }
    )
  ),
];
