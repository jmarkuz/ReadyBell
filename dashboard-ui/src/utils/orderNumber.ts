/** The number as typed on the receipt: digits, optionally with a leading "#". */
export function parseOrderNumber(input: string): number | null {
  const match = /^#?\s*(\d{1,6})$/.exec(input.trim());
  const n = match ? Number(match[1]) : NaN;
  return Number.isInteger(n) && n >= 1 ? n : null;
}
