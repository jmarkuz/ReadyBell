import { OrderStatus } from '@/types';

/**
 * Lifecycle transitions the backend allows, in the UI's status names
 * (CREATED → PREPARING → READY → COMPLETED, CANCELLED from any non-terminal state).
 */
export const NEXT_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  CREATED: ['PREPARING', 'CANCELLED'],
  PREPARING: ['READY', 'CANCELLED'],
  READY: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: [],
};

/** Menu wording for moving an order to a status. */
export const STATUS_ACTION_LABEL: Record<OrderStatus, string> = {
  CREATED: 'Mark as new',
  PREPARING: 'Start preparing',
  READY: 'Mark ready for pickup',
  COMPLETED: 'Mark completed',
  CANCELLED: 'Cancel order',
};
