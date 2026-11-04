import type { TestDataOrder } from '@/utils/api';

const STATUSES = ['CREATED', 'IN_PROGRESS', 'READY', 'COMPLETED', 'CANCELLED'];

/**
 * Parses the text of a test-data file: either a JSON array of orders or `{ "orders": [...] }`.
 * Throws an Error with a user-readable message when the file is not usable.
 */
export function parseTestDataFile(text: string): TestDataOrder[] {
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error('The file is not valid JSON.');
  }
  const orders = Array.isArray(json) ? json : (json as { orders?: unknown } | null)?.orders;
  if (!Array.isArray(orders) || orders.length === 0) {
    throw new Error('Expected a non-empty list of orders ({ "orders": [...] }).');
  }
  orders.forEach((o, i) => {
    const where = `Order #${i + 1}`;
    if (!o || typeof o !== 'object') throw new Error(`${where} is not an object.`);
    const { status, items } = o as TestDataOrder;
    if (status !== undefined && !STATUSES.includes(status)) {
      throw new Error(`${where}: status must be one of ${STATUSES.join(', ')}.`);
    }
    if (items !== undefined && !Array.isArray(items)) {
      throw new Error(`${where}: items must be a list.`);
    }
  });
  return orders as TestDataOrder[];
}
