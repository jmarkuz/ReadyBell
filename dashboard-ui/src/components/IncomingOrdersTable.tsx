import { useState, useMemo, useCallback, useEffect, memo } from 'react';
import { createPortal } from 'react-dom';
import { OrderRow, OrderStatus } from '@/types';
import { NEXT_STATUSES, STATUS_ACTION_LABEL } from '@/utils/orderStatus';
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
  /** Called when staff picks a new status from the row's menu (a status the lifecycle allows next). */
  onStatusChange?: (orderId: string, newStatus: OrderStatus) => void;
  sortBy?: 'eta' | 'status' | 'created' | 'id';
  sortOrder?: 'asc' | 'desc';
}

type SortField = 'id' | 'customerName' | 'itemsOrdered' | 'deliveryETA' | 'status';
type SortOrder = 'asc' | 'desc';
interface SortState { field: SortField; order: SortOrder }

/** Clicking the active column flips its direction; clicking another column starts it ascending. */
const nextSort = (prev: SortState, field: SortField): SortState =>
  prev.field === field
    ? { field, order: prev.order === 'asc' ? 'desc' : 'asc' }
    : { field, order: 'asc' };

const SORTABLE_COLUMNS: { field: SortField; label: string }[] = [
  { field: 'id', label: 'Order ID' },
  { field: 'customerName', label: 'Customer' },
  { field: 'itemsOrdered', label: 'Items' },
  { field: 'deliveryETA', label: 'ETA' },
  { field: 'status', label: 'Status' },
];

const ARIA_SORT = { asc: 'ascending', desc: 'descending' } as const;

function SortIndicator({ order }: { order: SortOrder | null }) {
  if (!order) return <span className="text-slate-300" aria-hidden="true">⋅</span>;
  const Icon = order === 'asc' ? ChevronUp : ChevronDown;
  return <Icon className="w-4 h-4 inline" aria-hidden="true" />;
}

function SortableHeader({ field, label, sort, onSort }: {
  field: SortField; label: string; sort: SortState; onSort: (field: SortField) => void;
}) {
  const active = sort.field === field;
  return (
    <th
      className="px-0 py-0 text-left text-sm font-semibold text-slate-600 dark:text-slate-300"
      scope="col"
      aria-sort={active ? ARIA_SORT[sort.order] : 'none'}
    >
      <button
        type="button"
        onClick={() => onSort(field)}
        className="flex w-full items-center gap-2 px-6 py-3 text-left font-semibold cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-500"
      >
        {label}
        <SortIndicator order={active ? sort.order : null} />
      </button>
    </th>
  );
}

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
  currentPage: controlledPage,
  onPageChange,
  onRowClick,
  onStatusChange,
  sortOrder: initialSortOrder = 'asc',
}: IncomingOrdersTableProps) {
  const [sort, setSort] = useState<SortState>({ field: 'deliveryETA', order: initialSortOrder });
  const { field: sortField, order: sortOrder } = sort;
  // The menu is rendered in a portal with fixed coordinates so the table's scroll container cannot clip it
  const [statusMenu, setStatusMenu] = useState<{ orderId: string; right: number; top?: number; bottom?: number } | null>(null);
  const statusMenuFor = statusMenu?.orderId ?? null;
  const setStatusMenuFor = useCallback((value: null) => setStatusMenu(value), []);

  const toggleStatusMenu = useCallback((orderId: string, anchor: HTMLElement, itemCount: number) => {
    setStatusMenu((open) => {
      if (open?.orderId === orderId) return null;
      const rect = anchor.getBoundingClientRect();
      const menuHeight = itemCount * 36 + 8;
      const right = Math.max(8, window.innerWidth - rect.right);
      return window.innerHeight - rect.bottom >= menuHeight + 8
        ? { orderId, right, top: rect.bottom + 4 }
        : { orderId, right, bottom: window.innerHeight - rect.top + 4 };   // not enough room below: open upwards
    });
  }, []);
  // Uncontrolled by default: the table pages itself unless the parent passes currentPage
  const [internalPage, setInternalPage] = useState(1);

  // Close the status menu on Escape or when clicking elsewhere
  useEffect(() => {
    if (!statusMenuFor) return undefined;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setStatusMenuFor(null); };
    const onClick = (e: MouseEvent) => {
      if (!(e.target as HTMLElement).closest('[role="menu"], [aria-haspopup="menu"]')) setStatusMenuFor(null);
    };
    const close = () => setStatusMenu(null);
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClick);
    window.addEventListener('resize', close);
    window.addEventListener('scroll', close, true);   // any scroll container: the anchor has moved
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onClick);
      window.removeEventListener('resize', close);
      window.removeEventListener('scroll', close, true);
    };
  }, [statusMenuFor]);

  // Sorting logic - memoized for performance
  const sortedOrders = useMemo(() => {
    const sorted = [...orders].sort((a, b) => {
      let compareA: string | number | Date | null | undefined = a[sortField];
      let compareB: string | number | Date | null | undefined = b[sortField];

      if (sortField === 'deliveryETA') {
        // Orders without an ETA sort last in ascending order
        compareA = compareA instanceof Date ? compareA.getTime() : Number.MAX_SAFE_INTEGER;
        compareB = compareB instanceof Date ? compareB.getTime() : Number.MAX_SAFE_INTEGER;
      } else if (sortField === 'status') {
        // Sort by status priority
        const statusPriority: Record<string, number> = {
          CREATED: 1,
          PREPARING: 2,
          READY: 3,
          COMPLETED: 4,
          CANCELLED: 5,
        };
        compareA = statusPriority[String(compareA)] || 99;
        compareB = statusPriority[String(compareB)] || 99;
      }

      if (compareA == null || compareB == null) return 0;
      if (compareA < compareB) return sortOrder === 'asc' ? -1 : 1;
      if (compareA > compareB) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });

    return sorted;
  }, [orders, sortField, sortOrder]);

  // Pagination logic — pages only appear once there are more orders than pageSize
  const totalPages = Math.ceil(sortedOrders.length / pageSize);
  // Clamp so filtering or a removed order never leaves us on a page that no longer exists
  const currentPage = Math.min(Math.max(1, controlledPage ?? internalPage), Math.max(1, totalPages));
  const goToPage = useCallback((page: number) => {
    setInternalPage(page);
    onPageChange?.(page);
  }, [onPageChange]);
  // A new sort order makes the old page number meaningless, so go back to the first page
  const handleSort = useCallback((field: SortField) => {
    setSort((prev) => nextSort(prev, field));
    setInternalPage(1);
    if (currentPage !== 1) onPageChange?.(1);
  }, [currentPage, onPageChange]);
  const paginatedOrders = useMemo(
    () => sortedOrders.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    [sortedOrders, currentPage, pageSize]
  );

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
              {SORTABLE_COLUMNS.map(({ field, label }) => (
                <SortableHeader key={field} field={field} label={label} sort={sort} onSort={handleSort} />
              ))}
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
                  {order.deliveryETA ? formatDistanceToNow(order.deliveryETA, { addSuffix: true }) : '—'}
                </td>
                <td className="px-6 py-4">
                  <OrderStatusBadge status={order.status} />
                </td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onRowClick?.(order.id)}
                      className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
                      aria-label={`View details for order ${order.orderId}`}
                      title="View Details"
                      type="button"
                    >
                      <Eye className="w-4 h-4 text-slate-600 dark:text-slate-400" aria-hidden="true" />
                    </button>
                    <div className="relative">
                      <button
                        onClick={(e) => toggleStatusMenu(order.id, e.currentTarget, NEXT_STATUSES[order.status].length)}
                        className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-40 disabled:cursor-not-allowed"
                        aria-label={`Update status for order ${order.orderId}, currently ${order.status}`}
                        aria-haspopup="menu"
                        aria-expanded={statusMenuFor === order.id}
                        disabled={NEXT_STATUSES[order.status].length === 0}
                        title={NEXT_STATUSES[order.status].length === 0 ? 'No further status changes' : 'Update Status'}
                        type="button"
                      >
                        <Edit2 className="w-4 h-4 text-slate-600 dark:text-slate-400" aria-hidden="true" />
                      </button>
                    </div>
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
              onClick={() => goToPage(Math.max(1, currentPage - 1))}
              disabled={currentPage === 1}
              className="px-3 py-1 text-sm border border-slate-200 dark:border-slate-700 rounded-full hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
              aria-label="Go to previous page"
              type="button"
            >
              Previous
            </button>
            <button
              onClick={() => goToPage(Math.min(totalPages, currentPage + 1))}
              disabled={currentPage === totalPages}
              className="px-3 py-1 text-sm border border-slate-200 dark:border-slate-700 rounded-full hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900"
              aria-label="Go to next page"
              type="button"
            >
              Next
            </button>
          </div>
        </div>
      )}
      {statusMenu && (() => {
        const order = sortedOrders.find((o) => o.id === statusMenu.orderId);
        if (!order) return null;
        return createPortal(
          <div
            role="menu"
            aria-label={`Change status of order ${order.orderId}`}
            style={{ position: 'fixed', right: statusMenu.right, top: statusMenu.top, bottom: statusMenu.bottom }}
            className="z-50 w-52 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 shadow-lg py-1"
          >
            {NEXT_STATUSES[order.status].map((target) => (
              <button
                key={target}
                role="menuitem"
                type="button"
                className="w-full text-left px-3 py-2 text-sm hover:bg-slate-100 dark:hover:bg-slate-800 focus:outline-none focus:bg-slate-100 dark:focus:bg-slate-800"
                onClick={() => {
                  setStatusMenu(null);
                  onStatusChange?.(order.id, target);
                }}
              >
                {STATUS_ACTION_LABEL[target]}
              </button>
            ))}
          </div>,
          document.body
        );
      })()}
    </div>
  );
});
