import { describe, it, expect } from 'vitest';
import { NEXT_STATUSES, STATUS_ACTION_LABEL } from './orderStatus';
import { OrderStatus } from '@/types';

describe('order status transitions', () => {
  const all: OrderStatus[] = ['CREATED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED'];

  it('follows the lifecycle: CREATED → PREPARING → READY → COMPLETED, CANCELLED from any open state', () => {
    expect(NEXT_STATUSES.CREATED).toEqual(['PREPARING', 'CANCELLED']);
    expect(NEXT_STATUSES.PREPARING).toEqual(['READY', 'CANCELLED']);
    expect(NEXT_STATUSES.READY).toEqual(['COMPLETED', 'CANCELLED']);
  });

  it('has no transitions out of terminal states', () => {
    expect(NEXT_STATUSES.COMPLETED).toEqual([]);
    expect(NEXT_STATUSES.CANCELLED).toEqual([]);
  });

  it('never offers CREATED or the current status as a target, and labels every status', () => {
    for (const status of all) {
      expect(NEXT_STATUSES[status]).not.toContain('CREATED');
      expect(NEXT_STATUSES[status]).not.toContain(status);
      expect(STATUS_ACTION_LABEL[status]).toBeTruthy();
    }
  });
});
