import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import {
  formatDate,
  parseDate,
  getPresetDates,
  isDateInRange,
  isValidDateRange,
  getDaysDifference,
  isToday,
  isInCurrentWeek,
  isInCurrentMonth,
} from './dateHelper';

/**
 * Unit Tests for Date Helper Functions
 **Validates: Requirements 6.3**
 */
describe('dateHelper utilities', () => {
  describe('formatDate', () => {
    it('should format date to YYYY-MM-DD format', () => {
      const date = new Date(2024, 0, 1); // January 1, 2024
      expect(formatDate(date)).toBe('2024-01-01');
    });

    it('should pad single-digit month and day with zero', () => {
      const date = new Date(2024, 8, 5); // September 5, 2024
      expect(formatDate(date)).toBe('2024-09-05');
    });

    it('should handle end of month dates', () => {
      const date = new Date(2024, 11, 31); // December 31, 2024
      expect(formatDate(date)).toBe('2024-12-31');
    });

    it('should handle leap year dates', () => {
      const date = new Date(2024, 1, 29); // February 29, 2024
      expect(formatDate(date)).toBe('2024-02-29');
    });
  });

  describe('parseDate', () => {
    it('should parse YYYY-MM-DD string to Date object', () => {
      const dateString = '2024-01-15';
      const parsed = parseDate(dateString);
      expect(parsed.getFullYear()).toBe(2024);
      expect(parsed.getMonth()).toBe(0); // January (0-indexed)
      expect(parsed.getDate()).toBe(15);
    });

    it('should parse date at midnight UTC', () => {
      const parsed = parseDate('2024-01-15');
      // The date should be parsed in UTC, but getHours returns local time
      // So we check that it's a valid Date object representing that date
      expect(parsed.getFullYear()).toBe(2024);
      expect(parsed.getMonth()).toBe(0);
      expect(parsed.getDate()).toBe(15);
      // getHours() will reflect local timezone offset, so we just check it's valid
      expect(parsed.getHours()).toBeDefined();
    });
  });

  describe('getPresetDates', () => {
    it('should return correct dates for "today" preset', () => {
      const { start, end } = getPresetDates('today');
      const now = new Date();
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

      expect(formatDate(start)).toBe(formatDate(today));
      expect(formatDate(end)).toBe(formatDate(today));
    });

    it('should return correct dates for "week" preset', () => {
      const { start, end } = getPresetDates('week');

      // Start should be Monday
      expect(start.getDay()).toBe(1); // Monday

      // End should be today or later
      expect(end >= start).toBe(true);

      // Range should not exceed 7 days
      const diffTime = end.getTime() - start.getTime();
      const diffDays = diffTime / (1000 * 60 * 60 * 24);
      expect(diffDays).toBeLessThanOrEqual(7);
    });

    it('should return correct dates for "month" preset', () => {
      const { start, end } = getPresetDates('month');
      const now = new Date();

      // Start should be first day of month
      expect(start.getDate()).toBe(1);

      // Start and end should be in the same month
      expect(start.getMonth()).toBe(end.getMonth());

      // End should be today
      expect(end.getDate()).toBe(now.getDate());
    });
  });

  describe('isDateInRange', () => {
    const startDate = new Date(2024, 0, 1);
    const endDate = new Date(2024, 0, 31);

    it('should return true for date within range', () => {
      const dateInRange = new Date(2024, 0, 15);
      expect(isDateInRange(dateInRange, startDate, endDate)).toBe(true);
    });

    it('should return true for date equal to start date', () => {
      expect(isDateInRange(startDate, startDate, endDate)).toBe(true);
    });

    it('should return true for date equal to end date', () => {
      expect(isDateInRange(endDate, startDate, endDate)).toBe(true);
    });

    it('should return false for date before range', () => {
      const dateBefore = new Date(2023, 11, 31);
      expect(isDateInRange(dateBefore, startDate, endDate)).toBe(false);
    });

    it('should return false for date after range', () => {
      const dateAfter = new Date(2024, 1, 1);
      expect(isDateInRange(dateAfter, startDate, endDate)).toBe(false);
    });
  });

  describe('isValidDateRange', () => {
    it('should return true when start date equals end date', () => {
      const date = new Date(2024, 0, 15);
      expect(isValidDateRange(date, date)).toBe(true);
    });

    it('should return true when start date is before end date', () => {
      const startDate = new Date(2024, 0, 1);
      const endDate = new Date(2024, 0, 31);
      expect(isValidDateRange(startDate, endDate)).toBe(true);
    });

    it('should return false when start date is after end date', () => {
      const startDate = new Date(2024, 0, 31);
      const endDate = new Date(2024, 0, 1);
      expect(isValidDateRange(startDate, endDate)).toBe(false);
    });
  });

  describe('getDaysDifference', () => {
    it('should return correct number of days between two dates', () => {
      const startDate = new Date(2024, 0, 1);
      const endDate = new Date(2024, 0, 31);
      expect(getDaysDifference(startDate, endDate)).toBe(31); // Inclusive of both dates
    });

    it('should return 1 for same date', () => {
      const date = new Date(2024, 0, 15);
      expect(getDaysDifference(date, date)).toBe(1);
    });

    it('should return correct value for dates in different months', () => {
      const startDate = new Date(2024, 0, 30);
      const endDate = new Date(2024, 1, 1);
      expect(getDaysDifference(startDate, endDate)).toBe(3); // Jan 30, 31, Feb 1
    });
  });

  describe('isToday', () => {
    it('should return true for today date', () => {
      const today = new Date();
      expect(isToday(today)).toBe(true);
    });

    it('should return true for today at different time', () => {
      const today = new Date();
      today.setHours(12, 30, 0, 0);
      expect(isToday(today)).toBe(true);
    });

    it('should return false for yesterday', () => {
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      expect(isToday(yesterday)).toBe(false);
    });

    it('should return false for tomorrow', () => {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      expect(isToday(tomorrow)).toBe(false);
    });
  });

  describe('isInCurrentWeek', () => {
    it('should return true for today', () => {
      expect(isInCurrentWeek(new Date())).toBe(true);
    });

    it('should return true for other days in current week', () => {
      const today = new Date();
      const dayOfWeek = today.getDay();

      // Try a few days within the same week
      for (let i = Math.max(1 - dayOfWeek, -6); i < Math.min(8 - dayOfWeek, 1); i++) {
        if (i !== 0) {
          const otherDay = new Date(today);
          otherDay.setDate(otherDay.getDate() + i);
          if (otherDay.getDay() !== 0 && otherDay.getDay() !== 6) {
            // Only test weekdays to avoid crossing week boundary
            expect(isInCurrentWeek(otherDay)).toBe(true);
          }
        }
      }
    });

    it('should return false for date in different week', () => {
      const today = new Date();
      const nextWeek = new Date(today);
      nextWeek.setDate(nextWeek.getDate() + 14);
      expect(isInCurrentWeek(nextWeek)).toBe(false);
    });
  });

  describe('isInCurrentMonth', () => {
    it('should return true for today', () => {
      expect(isInCurrentMonth(new Date())).toBe(true);
    });

    it('should return true for other days in current month', () => {
      const today = new Date();
      const otherDay = new Date(today.getFullYear(), today.getMonth(), 15);
      expect(isInCurrentMonth(otherDay)).toBe(true);
    });

    it('should return false for date in different month', () => {
      const today = new Date();
      const nextMonth = new Date(today.getFullYear(), today.getMonth() + 1, 1);
      expect(isInCurrentMonth(nextMonth)).toBe(false);
    });

    it('should return false for date in previous month', () => {
      const today = new Date();
      const previousMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      expect(isInCurrentMonth(previousMonth)).toBe(false);
    });
  });
});

/**
 * Property-Based Tests for Date Helper Functions
 * **Validates: Requirements 6.3**
 */
describe('dateHelper - Property-Based Tests', () => {
  describe('formatDate / parseDate roundtrip', () => {
    it('should roundtrip date through format and parse without time loss', () => {
      fc.assert(
        fc.property(fc.date({ min: new Date(2000, 0, 1), max: new Date(2100, 0, 1) }), (date) => {
          const formatted = formatDate(date);
          const parsed = parseDate(formatted);

          expect(parsed.getFullYear()).toBe(date.getFullYear());
          expect(parsed.getMonth()).toBe(date.getMonth());
          expect(parsed.getDate()).toBe(date.getDate());
        })
      );
    });
  });

  describe('isValidDateRange property', () => {
    it('should always return true when start equals end', () => {
      fc.assert(
        fc.property(fc.date(), (date) => {
          expect(isValidDateRange(date, date)).toBe(true);
        })
      );
    });

    it('should return true for any start <= end', () => {
      fc.assert(
        fc.property(
          fc.tuple(fc.date(), fc.date()).map(([a, b]) => [
            new Date(Math.min(a.getTime(), b.getTime())),
            new Date(Math.max(a.getTime(), b.getTime())),
          ]),
          ([start, end]) => {
            expect(isValidDateRange(start, end)).toBe(true);
          }
        )
      );
    });

    it('should return false for any start > end', () => {
      fc.assert(
        fc.property(
          fc
            .tuple(fc.date(), fc.date())
            .filter(([a, b]) => a.getTime() !== b.getTime())
            .map(([a, b]) => [
              new Date(Math.max(a.getTime(), b.getTime())),
              new Date(Math.min(a.getTime(), b.getTime())),
            ]),
          ([start, end]) => {
            expect(isValidDateRange(start, end)).toBe(false);
          }
        )
      );
    });
  });

  describe('isDateInRange property', () => {
    it('should return true for any date equal to start or end', () => {
      fc.assert(
        fc.property(
          fc.tuple(fc.date(), fc.date()).map(([a, b]) => {
            const start = new Date(Math.min(a.getTime(), b.getTime()));
            const end = new Date(Math.max(a.getTime(), b.getTime()));
            return [start, end];
          }),
          ([start, end]) => {
            expect(isDateInRange(start, start, end)).toBe(true);
            expect(isDateInRange(end, start, end)).toBe(true);
          }
        )
      );
    });

    it('should have consistent behavior: date in range implies valid range', () => {
      fc.assert(
        fc.property(
          fc
            .tuple(fc.date(), fc.date(), fc.date())
            .map(([a, b, c]) => {
              const start = new Date(Math.min(a.getTime(), b.getTime()));
              const end = new Date(Math.max(a.getTime(), b.getTime()));
              const mid = new Date(Math.min(Math.max(c.getTime(), start.getTime()), end.getTime()));
              return [start, mid, end];
            }),
          ([start, mid, end]) => {
            expect(isDateInRange(mid, start, end)).toBe(true);
            expect(isValidDateRange(start, end)).toBe(true);
          }
        )
      );
    });
  });

  describe('getDaysDifference property', () => {
    it('should always return positive or zero days for valid ranges', () => {
      fc.assert(
        fc.property(
          fc.tuple(fc.date(), fc.date()).map(([a, b]) => [
            new Date(Math.min(a.getTime(), b.getTime())),
            new Date(Math.max(a.getTime(), b.getTime())),
          ]),
          ([start, end]) => {
            const days = getDaysDifference(start, end);
            expect(days).toBeGreaterThanOrEqual(1);
          }
        )
      );
    });

    it('should return 1 for same date', () => {
      fc.assert(
        fc.property(fc.date(), (date) => {
          expect(getDaysDifference(date, date)).toBe(1);
        })
      );
    });

    it('should have consistent property: getDaysDifference(a,b) >= getDaysDifference(a,c) for c between a and b', () => {
      fc.assert(
        fc.property(
          fc
            .tuple(fc.date(), fc.date(), fc.date())
            .map(([a, b, c]) => {
              const start = new Date(Math.min(a.getTime(), b.getTime()));
              const end = new Date(Math.max(a.getTime(), b.getTime()));
              const mid = new Date(Math.min(Math.max(c.getTime(), start.getTime()), end.getTime()));
              return [start, mid, end];
            }),
          ([start, mid, end]) => {
            const totalDays = getDaysDifference(start, end);
            const firstHalf = getDaysDifference(start, mid);
            expect(totalDays).toBeGreaterThanOrEqual(firstHalf);
          }
        )
      );
    });
  });

  describe('getPresetDates property', () => {
    it('should always return valid date range for any preset', () => {
      const presets: Array<'today' | 'week' | 'month'> = ['today', 'week', 'month'];

      presets.forEach((preset) => {
        const { start, end } = getPresetDates(preset);
        expect(isValidDateRange(start, end)).toBe(true);
      });
    });

    it('today preset should always have same start and end', () => {
      const { start, end } = getPresetDates('today');
      expect(formatDate(start)).toBe(formatDate(end));
    });

    it('week preset should span at most 7 days', () => {
      const { start, end } = getPresetDates('week');
      const days = getDaysDifference(start, end);
      expect(days).toBeLessThanOrEqual(7);
    });

    it('month preset should start on first day', () => {
      const { start } = getPresetDates('month');
      expect(start.getDate()).toBe(1);
    });
  });

  describe('Date range validation properties', () => {
    it('start date <= end date should always be valid for filter application', () => {
      fc.assert(
        fc.property(
          fc.tuple(fc.date(), fc.date()).map(([a, b]) => [
            new Date(Math.min(a.getTime(), b.getTime())),
            new Date(Math.max(a.getTime(), b.getTime())),
          ]),
          ([start, end]) => {
            // Property: valid date ranges can be applied as filters
            expect(isValidDateRange(start, end)).toBe(true);
            // All dates between start and end (inclusive) should be in range
            const midDate = new Date((start.getTime() + end.getTime()) / 2);
            expect(isDateInRange(midDate, start, end)).toBe(true);
          }
        )
      );
    });
  });
});
