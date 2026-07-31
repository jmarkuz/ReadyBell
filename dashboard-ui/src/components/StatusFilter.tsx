import { useState, useCallback } from 'react';
import { ChevronDown, X } from 'lucide-react';
import { OrderStatus } from '@/types';

interface StatusFilterProps {
  onApply?: (statuses: OrderStatus[]) => void;
  onCancel?: () => void;
  selectedStatuses?: OrderStatus[];
}

/**
 * Status Filter Dropdown Component
 * 
 * Provides filtering by order status with:
 * - Checkboxes for each status: CREATED, PREPARING, READY, COMPLETED
 * - Select/Clear All toggle
 * - Apply and Cancel buttons
 * - Badge showing count of active filters
 * 
 * Status options available:
 * - CREATED: New orders
 * - PREPARING: Being prepared
 * - READY: Ready for pickup
 * - COMPLETED: Completed orders
 */
export default function StatusFilter({
  onApply,
  onCancel,
  selectedStatuses = [],
}: StatusFilterProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [tempSelectedStatuses, setTempSelectedStatuses] = useState<OrderStatus[]>(selectedStatuses);

  // All available status options (excluding CANCELLED per design - task specifies CREATED, PREPARING, READY, COMPLETED)
  const availableStatuses: OrderStatus[] = ['CREATED', 'PREPARING', 'READY', 'COMPLETED'];

  // Status labels for display
  const statusLabels: Record<OrderStatus, string> = {
    CREATED: 'New Order',
    PREPARING: 'Preparing',
    READY: 'Ready for Pickup',
    COMPLETED: 'Completed',
    CANCELLED: 'Cancelled',
  };

  // Toggle individual checkbox
  const handleToggleStatus = useCallback((status: OrderStatus) => {
    setTempSelectedStatuses((prev) => {
      if (prev.includes(status)) {
        return prev.filter((s) => s !== status);
      } else {
        return [...prev, status];
      }
    });
  }, []);

  // Select all statuses
  const handleSelectAll = useCallback(() => {
    setTempSelectedStatuses(availableStatuses);
  }, []);

  // Clear all statuses
  const handleClearAll = useCallback(() => {
    setTempSelectedStatuses([]);
  }, []);

  // Apply filters and close dropdown
  const handleApply = useCallback(() => {
    onApply?.(tempSelectedStatuses);
    setIsOpen(false);
  }, [tempSelectedStatuses, onApply]);

  // Cancel and reset to previous state
  const handleCancel = useCallback(() => {
    setTempSelectedStatuses(selectedStatuses);
    setIsOpen(false);
    onCancel?.();
  }, [selectedStatuses, onCancel]);

  // Check if all statuses are selected
  const allSelected = tempSelectedStatuses.length === availableStatuses.length;

  return (
    <div className="relative inline-block">
      {/* Filter button with badge */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2 px-3 py-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors relative focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950"
        aria-label={`Status filter ${selectedStatuses.length > 0 ? `(${selectedStatuses.length} selected)` : ''}`}
        aria-expanded={isOpen}
        aria-haspopup="menu"
        type="button"
      >
        <span className="text-sm font-medium">Status</span>
        <ChevronDown className={`w-4 h-4 transition-transform ${isOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
        
        {/* Active filters badge */}
        {selectedStatuses.length > 0 && (
          <span 
            className="absolute -top-2 -right-2 bg-blue-600 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
            aria-label={`${selectedStatuses.length} statuses selected`}
          >
            {selectedStatuses.length}
          </span>
        )}
      </button>

      {/* Dropdown menu */}
      {isOpen && (
        <div 
          className="absolute top-full mt-2 right-0 w-64 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg shadow-lg z-50"
          role="menu"
          aria-label="Status filter options"
        >
          {/* Header with Select/Clear All */}
          <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-700 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-white">Filter by Status</h3>
            <button
              onClick={() => setIsOpen(false)}
              className="p-1 hover:bg-slate-100 dark:hover:bg-slate-700 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label="Close filter dropdown"
              type="button"
            >
              <X className="w-4 h-4" aria-hidden="true" />
            </button>
          </div>

          {/* Checkbox list */}
          <fieldset className="px-4 py-2 space-y-2" role="group" aria-label="Status options">
            <legend className="sr-only">Select statuses to filter by</legend>
            {availableStatuses.map((status) => (
              <label
                key={status}
                className="flex items-center gap-3 p-2 hover:bg-slate-50 dark:hover:bg-slate-700 rounded cursor-pointer transition-colors focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2 dark:focus-within:ring-offset-slate-800"
              >
                <input
                  type="checkbox"
                  checked={tempSelectedStatuses.includes(status)}
                  onChange={() => handleToggleStatus(status)}
                  className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-2 focus:ring-blue-500 cursor-pointer"
                  aria-label={`Filter by ${statusLabels[status]}`}
                />
                <span className="text-sm flex-1 text-slate-900 dark:text-slate-100">{statusLabels[status]}</span>
              </label>
            ))}
          </fieldset>

          {/* Select All / Clear All buttons */}
          <div className="px-4 py-2 border-t border-slate-200 dark:border-slate-700 flex gap-2">
            <button
              onClick={handleSelectAll}
              className="flex-1 px-3 py-2 text-xs font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
              disabled={allSelected}
              type="button"
              aria-label="Select all statuses"
            >
              Select All
            </button>
            <button
              onClick={handleClearAll}
              className="flex-1 px-3 py-2 text-xs font-medium text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
              disabled={tempSelectedStatuses.length === 0}
              type="button"
              aria-label="Clear all statuses"
            >
              Clear All
            </button>
          </div>

          {/* Apply / Cancel buttons */}
          <div className="px-4 py-3 border-t border-slate-200 dark:border-slate-700 flex gap-2">
            <button
              onClick={handleCancel}
              className="flex-1 px-3 py-2 text-sm font-medium bg-slate-100 dark:bg-slate-700 text-slate-900 dark:text-slate-100 rounded hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
              type="button"
            >
              Cancel
            </button>
            <button
              onClick={handleApply}
              className="flex-1 px-3 py-2 text-sm font-medium bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
              type="button"
            >
              Apply
            </button>
          </div>
        </div>
      )}

      {/* Overlay to close dropdown when clicking outside */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40"
          onClick={() => setIsOpen(false)}
          role="presentation"
          aria-hidden="true"
        />
      )}
    </div>
  );
}
