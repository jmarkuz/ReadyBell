import React, { useState, useCallback, useMemo } from 'react';
import { Calendar, X } from 'lucide-react';
import { formatDate, getPresetDates } from '@/utils/dateHelper';

/**
 * DateRangeFilterProps - Props for DateRangeFilter component
 */
export interface DateRangeFilterProps {
  /** Callback when date range is applied */
  onApply?: (startDate: Date, endDate: Date) => void;
  /** Callback when filter is cleared */
  onClear?: () => void;
  /** Initial start date (optional) */
  initialStartDate?: Date;
  /** Initial end date (optional) */
  initialEndDate?: Date;
  /** CSS class name */
  className?: string;
  /** Show in expanded or collapsed state */
  isOpen?: boolean;
}

/**
 * DateRangeFilter Component
 *
 * Provides date range filtering for orders with:
 * - Date picker inputs (start and end date)
 * - Preset options: Today, This Week, This Month, Custom
 * - Validation (start date ≤ end date)
 * - Visual feedback for invalid ranges
 * - Keyboard accessible controls
 *
 * Accessibility:
 * - Semantic HTML with proper labels
 * - aria-label and aria-describedby for inputs
 * - Focus indicators on all interactive elements
 * - Keyboard navigation support
 */
export const DateRangeFilter = React.forwardRef<HTMLDivElement, DateRangeFilterProps>(
  function DateRangeFilter(
    {
      onApply,
      onClear,
      initialStartDate,
      initialEndDate,
      className = '',
      isOpen = true,
    },
    ref
  ) {
    // State management for date range
    const [startDate, setStartDate] = useState<string>(
      initialStartDate ? formatDate(initialStartDate) : ''
    );
    const [endDate, setEndDate] = useState<string>(
      initialEndDate ? formatDate(initialEndDate) : ''
    );
    const [selectedPreset, setSelectedPreset] = useState<string | null>(null);

    // Validation: ensure start date <= end date
    const isValidDateRange = useMemo(() => {
      if (!startDate || !endDate) return true; // Empty ranges are valid (no filter)
      const start = new Date(startDate);
      const end = new Date(endDate);
      return start <= end;
    }, [startDate, endDate]);

    // Get error message for invalid range
    const validationError = useMemo(() => {
      if (!startDate || !endDate) return '';
      if (!isValidDateRange) {
        return 'Start date must be before or equal to end date';
      }
      return '';
    }, [startDate, endDate, isValidDateRange]);

    // Handle preset selection
    const handlePresetSelect = useCallback((preset: 'today' | 'week' | 'month') => {
      const dates = getPresetDates(preset);
      setStartDate(formatDate(dates.start));
      setEndDate(formatDate(dates.end));
      setSelectedPreset(preset);
    }, []);

    // Handle custom date input
    const handleStartDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
      setStartDate(e.target.value);
      setSelectedPreset(null);
    }, []);

    const handleEndDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
      setEndDate(e.target.value);
      setSelectedPreset(null);
    }, []);

    // Handle apply action
    const handleApply = useCallback(() => {
      if (!startDate || !endDate) {
        return;
      }

      if (!isValidDateRange) {
        return;
      }

      const start = new Date(startDate);
      const end = new Date(endDate);

      // Set end date to end of day (23:59:59.999)
      end.setHours(23, 59, 59, 999);

      onApply?.(start, end);
    }, [startDate, endDate, isValidDateRange, onApply]);

    // Handle clear action
    const handleClear = useCallback(() => {
      setStartDate('');
      setEndDate('');
      setSelectedPreset(null);
      onClear?.();
    }, [onClear]);

    // Handle keyboard shortcuts
    const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLDivElement>) => {
      if (e.key === 'Enter') {
        handleApply();
      } else if (e.key === 'Escape') {
        handleClear();
      }
    }, [handleApply, handleClear]);

    if (!isOpen) {
      return null;
    }

    return (
      <div
        ref={ref}
        className={`p-4 bg-white dark:bg-slate-900 rounded-lg shadow-md border border-slate-200 dark:border-slate-800 ${className}`}
        role="region"
        aria-label="Date range filter"
        onKeyDown={handleKeyDown}
      >
        <div className="space-y-4">
          {/* Header */}
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
              <Calendar className="w-4 h-4" />
              Filter by Date Range
            </h3>
            {(startDate || endDate) && (
              <button
                onClick={handleClear}
                className="p-1 text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition-colors"
                aria-label="Clear date filter"
                title="Clear filter"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* Preset Buttons */}
          <div className="flex gap-2 flex-wrap">
            <button
              onClick={() => handlePresetSelect('today')}
              className={`px-3 py-2 rounded text-sm font-medium transition-colors ${
                selectedPreset === 'today'
                  ? 'bg-blue-500 text-white dark:bg-blue-600'
                  : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
              }`}
              aria-pressed={selectedPreset === 'today'}
            >
              Today
            </button>
            <button
              onClick={() => handlePresetSelect('week')}
              className={`px-3 py-2 rounded text-sm font-medium transition-colors ${
                selectedPreset === 'week'
                  ? 'bg-blue-500 text-white dark:bg-blue-600'
                  : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
              }`}
              aria-pressed={selectedPreset === 'week'}
            >
              This Week
            </button>
            <button
              onClick={() => handlePresetSelect('month')}
              className={`px-3 py-2 rounded text-sm font-medium transition-colors ${
                selectedPreset === 'month'
                  ? 'bg-blue-500 text-white dark:bg-blue-600'
                  : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
              }`}
              aria-pressed={selectedPreset === 'month'}
            >
              This Month
            </button>
          </div>

          {/* Custom Date Inputs */}
          <div className="space-y-3 border-t border-slate-200 dark:border-slate-800 pt-3">
            <p className="text-xs text-slate-600 dark:text-slate-400 font-medium">Or select custom dates:</p>

            <div className="grid grid-cols-2 gap-3">
              {/* Start Date Input */}
              <div className="space-y-1">
                <label
                  htmlFor="start-date"
                  className="block text-xs font-medium text-slate-600 dark:text-slate-400"
                >
                  Start Date
                </label>
                <input
                  id="start-date"
                  type="date"
                  value={startDate}
                  onChange={handleStartDateChange}
                  className={`w-full px-3 py-2 text-sm rounded border transition-colors
                    bg-white dark:bg-slate-800 text-slate-900 dark:text-white
                    ${
                      validationError
                        ? 'border-red-500 dark:border-red-500 focus:ring-red-500'
                        : 'border-slate-300 dark:border-slate-700 focus:ring-blue-500'
                    }
                    focus:outline-none focus:ring-2 focus:ring-offset-0
                  `}
                  aria-label="Start date"
                  aria-describedby={validationError ? 'date-error' : undefined}
                />
              </div>

              {/* End Date Input */}
              <div className="space-y-1">
                <label
                  htmlFor="end-date"
                  className="block text-xs font-medium text-slate-600 dark:text-slate-400"
                >
                  End Date
                </label>
                <input
                  id="end-date"
                  type="date"
                  value={endDate}
                  onChange={handleEndDateChange}
                  className={`w-full px-3 py-2 text-sm rounded border transition-colors
                    bg-white dark:bg-slate-800 text-slate-900 dark:text-white
                    ${
                      validationError
                        ? 'border-red-500 dark:border-red-500 focus:ring-red-500'
                        : 'border-slate-300 dark:border-slate-700 focus:ring-blue-500'
                    }
                    focus:outline-none focus:ring-2 focus:ring-offset-0
                  `}
                  aria-label="End date"
                  aria-describedby={validationError ? 'date-error' : undefined}
                />
              </div>
            </div>

            {/* Error Message */}
            {validationError && (
              <div
                id="date-error"
                className="text-sm text-red-600 dark:text-red-400 flex items-center gap-2"
                role="alert"
              >
                <span className="inline-block w-1 h-1 bg-red-600 dark:bg-red-400 rounded-full" />
                {validationError}
              </div>
            )}
          </div>

          {/* Action Buttons */}
          <div className="flex gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
            <button
              onClick={handleApply}
              disabled={!startDate || !endDate || !isValidDateRange}
              className={`flex-1 px-3 py-2 rounded-full text-sm font-medium transition-colors
                ${
                  startDate && endDate && isValidDateRange
                    ? 'bg-blue-500 text-white hover:bg-blue-600 dark:bg-blue-600 dark:hover:bg-blue-700 cursor-pointer'
                    : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-500 cursor-not-allowed'
                }
              `}
              aria-label="Apply date filter"
            >
              Apply
            </button>
            <button
              onClick={handleClear}
              className="flex-1 px-3 py-2 rounded text-sm font-medium transition-colors
                bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300
                hover:bg-slate-200 dark:hover:bg-slate-700"
              aria-label="Clear date filter"
            >
              Clear
            </button>
          </div>
        </div>
      </div>
    );
  }
);

DateRangeFilter.displayName = 'DateRangeFilter';
