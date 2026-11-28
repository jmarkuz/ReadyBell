
/** Format a Date object to YYYY-MM-DD string (ISO format without time) */
export function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Parse an ISO date string (YYYY-MM-DD) to a Date object */
export function parseDate(dateString: string): Date {
  return new Date(`${dateString}T00:00:00Z`);
}

export function getPresetDates(
  preset: 'today' | 'week' | 'month'
): { start: Date; end: Date } {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  switch (preset) {
    case 'today':
      return {
        start: new Date(today),
        end: new Date(today),
      };

    case 'week': {
      const dayOfWeek = today.getDay();
      const daysToMonday = today.getDate() - dayOfWeek + (dayOfWeek === 0 ? -6 : 1);
      const monday = new Date(today.setDate(daysToMonday));

      return {
        start: new Date(monday),
        end: new Date(now.getFullYear(), now.getMonth(), now.getDate()),
      };
    }

    case 'month': {
      const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
      const lastDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      return {
        start: firstDay,
        end: lastDay,
      };
    }

    default:
      return {
        start: new Date(today),
        end: new Date(today),
      };
  }
}

/** Check if a date is within a range (inclusive) */
export function isDateInRange(date: Date, startDate: Date, endDate: Date): boolean {
  const dateTime = date.getTime();
  const startTime = startDate.getTime();
  const endTime = endDate.getTime();
  return dateTime >= startTime && dateTime <= endTime;
}

/** Check if start date is <= end date */
export function isValidDateRange(startDate: Date, endDate: Date): boolean {
  return startDate <= endDate;
}

/** Get the number of days between two dates */
export function getDaysDifference(startDate: Date, endDate: Date): number {
  const diffTime = Math.abs(endDate.getTime() - startDate.getTime());
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1; // +1 to include both start and end
  return diffDays;
}

/** Check if a date is today */
export function isToday(date: Date): boolean {
  const today = new Date();
  return (
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()
  );
}

/** Check if a date is in the current week */
export function isInCurrentWeek(date: Date): boolean {
  const today = new Date();
  const dayOfWeek = today.getDay();
  const daysToMonday = today.getDate() - dayOfWeek + (dayOfWeek === 0 ? -6 : 1);
  const monday = new Date(today.getFullYear(), today.getMonth(), daysToMonday);
  // The week ends where the next one begins; comparing with Sunday 00:00 would drop all of Sunday
  const nextMonday = new Date(monday);
  nextMonday.setDate(nextMonday.getDate() + 7);

  return date >= monday && date < nextMonday;
}

/** Check if a date is in the current month */
export function isInCurrentMonth(date: Date): boolean {
  const today = new Date();
  return (
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth()
  );
}
