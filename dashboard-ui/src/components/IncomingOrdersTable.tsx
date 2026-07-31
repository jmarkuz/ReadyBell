import { useState, useMemo, useCallback, memo } from 'react';
import { OrderRow } from '@/types';
import OrderStatusBadge from './OrderStatusBadge';
import { ChevronUp, ChevronDown, Eye, Edit2 } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

interface IncomingOrdersTableProps {
  orders: OrderRow[];
  isLoading?: boolean;
  pageSize?: number;
  currentPage?: number;
  onPageChange?: (page: number) => void;
  onRowClick?: (orderId: string) => void;
  onStatusChange?: (orderId: string, status: string) => void;
  sortBy?: 'eta' | 'status' | 'created' | 'id';
  sortOrder?: 'asc' | 'desc';
}

type SortField = 'id' | 'customerName' | 'itemsOrdered' | 'deliveryETA' | 'status';

/**
 * Incoming Orders Table Component
 * 
 * Displays paginated, sortable list of incoming orders with:
 * - Columns: Order ID, Customer Name, Items, Delivery ETA, Status, Actions
 * - Sortable columns (click header to toggle sort)
 * - Pagination controls
 * - Status badges with color-coding
 * - Action buttons (View Details, Update Status)
 * - Empty state when no orders
 * - Loading skeleton state
 * 
 * Responsive:
 * - Desktop: All columns visible
 * - Tablet/Mobile: Scrollable horizontally, actions in menu
 * 
 * Accessibility:
 * - Semantic <table> with proper headers
 * - aria-sort on sortable headers
 * - aria-label on buttons
 * 
 * Performance:
 * - Memoized with React.memo to prevent unnecessary re-renders
 * - useMemo for sorted/paginated order lists
 * - useCallback for handlers to maintain stable references
 */
export default memo(function IncomingOrdersTable({
  orders = [],
  isLoading = false,
  pageSize = 10,
  currentPage = 1,
  onPageChange,
  onRowClick,
  onStatusChange,
  sortOrder: initialSortOrder = 'asc',
}: IncomingOrdersTableProps) {
  const [sortField, setSortField] = useState<SortField>('deliveryETA');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>(initialSortOrder);

  // Handle column sort with useCallback for stable reference
  const handleSort = useCallback((field: SortField) => {
    setSortField((prevField) => {
      setSortOrder((prevOrder) => {
        if (prevField === field) {
          return prevOrder === 'asc' ? 'desc' : 'asc';
        } else {
          return 'asc';
        }
      });
      return prevField === field ? prevField : field;
    });
  }, []);

  // Sorting logic - memoized for performance
  const sortedOrders = useMemo(() => {
    const sorted = [...orders].sort((a, b) => {
      let compareA: any = a[sortField];
      let compareB: any = b[sortField];

      if (sortField === 'deliveryETA') {
        compareA = compareA.getTime();
        compareB = compareB.getTime();
      } else if (sortField === 'status') {
        // Sort by status priority
        const statusPriority: Record<string, number> = {
          CREATED: 1,
          PREPARING: 2,
          READY: 3,
          COMPLETED: 4,
          CANCELLED: 5,
        };
        compareA = statusPriority[compareA] || 99;
        compareB = statusPriority[compareB] || 99;
      }

      if (compareA < compareB) return sortOrder === 'asc' ? -1 : 1;
      if (compareA > compareB) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });

    return sorted;
  }, [orders, sortField, sortOrder]);

  // Pagination logic
  const totalPages = Math.ceil(sortedOrders.length / pageSize);
  const paginatedOrders = useMemo(
    () => sortedOrders.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    [sortedOrders, currentPage, pageSize]
  );

  const SortIndicator = memo(({ field }: { field: SortField }) => {
    if (sortField !== field) return <span className="text-slate-300">⋅</span>;
    return sortOrder === 'asc' ? (
      <ChevronUp className="w-4 h-4 inline" />
    ) : (
      <ChevronDown className="w-4 h-4 inline" />
    );
  });

  if (isLoading) {
    return (
      <div className="bg-white dark:bg-slate-900 rounded-lg shadow-md overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full" role="table" aria-label="Loading orders table">
            <thead className="bg-slate-50 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300" scope="col">Order ID</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300" scope="col">Customer</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300" scope="col">Items</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300" scope="col">ETA</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300" scope="col">Status</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300" scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {[...Array(5)].map((_, i) => (
                <tr key={i} className="border-b border-slate-200 dark:border-slate-800 animate-pulse">
                  <td className="px-6 py-4"><div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-20" /></td>
                  <td className="px-6 py-4"><div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-32" /></td>
                  <td className="px-6 py-4"><div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-40" /></td>
                  <td className="px-6 py-4"><div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-24" /></td>
                  <td className="px-6 py-4"><div className="h-6 bg-slate-200 dark:bg-slate-800 rounded w-20" /></td>
                  <td className="px-6 py-4"><div className="h-8 bg-slate-200 dark:bg-slate-800 rounded w-20" /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  // Empty state
  if (orders.length === 0) {
    return (
      <div className="bg-white dark:bg-slate-900 rounded-lg shadow-md p-12 text-center">
        <p className="text-slate-500 dark:text-slate-400 text-lg">No orders found.</p>
        <p className="text-slate-400 dark:text-slate-500 text-sm mt-2">
          Create an order to get started.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-slate-900 rounded-lg shadow-md overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse" role="table" aria-label="Incoming orders list">
          <caption className="sr-only">List of incoming orders with sorting and pagination</caption>
          <thead className="bg-slate-50 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700">
            <tr>
              <th
                className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300 cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                onClick={() => handleSort('id')}
                scope="col"
                aria-sort={sortField === 'id' ? (sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    handleSort('id');
                  }
                }}
              >
                <div className="flex items-center gap-2">
                  Order ID
                  <SortIndicator field="id" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300 cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                onClick={() => handleSort('customerName')}
                scope="col"
                aria-sort={sortField === 'customerName' ? (sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    handleSort('customerName');
                  }
                }}
              >
                <div className="flex items-center gap-2">
                  Customer
                  <SortIndicator field="customerName" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300 cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                onClick={() => handleSort('itemsOrdered')}
                scope="col"
                aria-sort={sortField === 'itemsOrdered' ? (sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    handleSort('itemsOrdered');
                  }
                }}
              >
                <div className="flex items-center gap-2">
                  Items
                  <SortIndicator field="itemsOrdered" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300 cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                onClick={() => handleSort('deliveryETA')}
                scope="col"
                aria-sort={sortField === 'deliveryETA' ? (sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    handleSort('deliveryETA');
                  }
                }}
              >
                <div className="flex items-center gap-2">
                  ETA
                  <SortIndicator field="deliveryETA" />
                </div>
              </th>
              <th
                className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300 cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                onClick={() => handleSort('status')}
                scope="col"
                aria-sort={sortField === 'status' ? (sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    handleSort('status');
                  }
                }}
              >
                <div className="flex items-center gap-2">
                  Status
                  <SortIndicator field="status" />
                </div>
              </th>
              <th className="px-6 py-3 text-left text-sm font-semibold text-slate-600 dark:text-slate-300" scope="col">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {paginatedOrders.map((order) => (
              <tr
                key={order.id}
                className="border-b border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
              >
                <td className="px-6 py-4 text-sm font-medium text-slate-900 dark:text-slate-100">
                  {order.orderId}
                </td>
                <td className="px-6 py-4 text-sm text-slate-600 dark:text-slate-400">
                  {order.customerName || 'N/A'}
                </td>
                <td className="px-6 py-4 text-sm text-slate-600 dark:text-slate-400">
                  <span title={order.itemsOrdered} className="line-clamp-1">
                    {order.itemsOrdered}
                  </span>
                </td>
                <td className="px-6 py-4 text-sm text-slate-600 dark:text-slate-400">
                  {formatDistanceToNow(order.deliveryETA, { addSuffix: true })}
                </td>
                <td className="px-6 py-4">
                  <OrderStatusBadge status={order.status} />
                </td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onRowClick?.(order.id)}
                      className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
                      aria-label={`View details for order ${order.orderId}`}
                      title="View Details"
                      type="button"
                    >
                      <Eye className="w-4 h-4 text-slate-600 dark:text-slate-400" aria-hidden="true" />
                    </button>
                    <button
                      onClick={() => onStatusChange?.(order.id, order.status)}
                      className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
                      aria-label={`Update status for order ${order.orderId}, currently ${order.status}`}
                      title="Update Status"
                      type="button"
                    >
                      <Edit2 className="w-4 h-4 text-slate-600 dark:text-slate-400" aria-hidden="true" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-6 py-4 bg-slate-50 dark:bg-slate-800 border-t border-slate-200 dark:border-slate-700" role="region" aria-label="Table pagination">
          <div className="text-sm text-slate-600 dark:text-slate-400">
            Page {currentPage} of {totalPages} ({sortedOrders.length} total orders)
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onPageChange?.(Math.max(1, currentPage - 1))}
              disabled={currentPage === 1}
              className="px-3 py-1 text-sm border border-slate-200 dark:border-slate-700 rounded hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
              aria-label="Go to previous page"
              type="button"
            >
              Previous
            </button>
            <button
              onClick={() => onPageChange?.(Math.min(totalPages, currentPage + 1))}
              disabled={currentPage === totalPages}
              className="px-3 py-1 text-sm border border-slate-200 dark:border-slate-700 rounded hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
              aria-label="Go to next page"
              type="button"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
});
