import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { parseTestDataFile } from './testDataFile';

describe('parseTestDataFile', () => {
  it('accepts { orders: [...] } and a bare array', () => {
    expect(parseTestDataFile('{"orders":[{"customerName":"A"}]}')).toHaveLength(1);
    expect(parseTestDataFile('[{"status":"READY"},{}]')).toHaveLength(2);
  });

  it('accepts the bundled 50-record sample file', () => {
    const text = readFileSync('test-data/test-orders-50.json', 'utf8');
    expect(parseTestDataFile(text)).toHaveLength(50);
  });

  it.each([
    ['not json', 'not valid JSON'],
    ['{"orders":[]}', 'non-empty list'],
    ['{"foo":1}', 'non-empty list'],
    ['[1]', 'Order #1 is not an object'],
    ['[{"status":"BOGUS"}]', 'Order #1: status must be one of'],
    ['[{},{"items":"x"}]', 'Order #2: items must be a list'],
  ])('rejects %s', (text, message) => {
    expect(() => parseTestDataFile(text)).toThrow(message);
  });
});
