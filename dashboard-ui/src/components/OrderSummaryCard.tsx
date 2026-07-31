import React, { memo } from 'react';
import { Trend } from '@/types';
import { TrendingUp, TrendingDown } from 'lucide-react';

interface OrderSummaryCardProps {
  title: string;
  value: number;
  icon?: React.ComponentType<{ className?: string }>;
  trend?: Trend;
  backgroundColor?: string;
  onClick?: () => void;
  isLoading?: boolean;
}

/**
 * Order Summary Card Component
 * 
 * Displays a single order metric with:
 * - Large metric value
 * - Descriptive label
 * - Icon
 * - Trend indicator (up/down with percentage, optional)
 * - Hover effect (shadow increase, slight scale)
 * - Loading skeleton state
 * 
 * Accessibility:
 * - aria-label with full context
 * - Keyboard accessible (Tab, Enter)
 */
export default memo(function OrderSummaryCard({
  title,
  value,
  icon: Icon,
  trend,
  onClick,
  isLoading = false,
}: OrderSummaryCardProps) {
  if (isLoading) {
    return (
      <div
        className="p-6 bg-white dark:bg-slate-900 rounded-lg shadow-md border border-slate-200 dark:border-slate-800 animate-pulse"
        role="status"
        aria-label={`Loading ${title}`}
      >
        <div className="flex items-center justify-between">
          <div className="flex-1">
            <div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-24 mb-3" />
            <div className="h-8 bg-slate-200 dark:bg-slate-800 rounded w-16 mb-2" />
            <div className="h-3 bg-slate-200 dark:bg-slate-800 rounded w-32" />
          </div>
          <div className="w-12 h-12 bg-slate-200 dark:bg-slate-800 rounded-lg" />
        </div>
      </div>
    );
  }

  return (
    <button
      onClick={onClick}
      className={`
        p-6 bg-white dark:bg-slate-900 rounded-lg shadow-md border border-slate-200 dark:border-slate-800
        hover:shadow-lg hover:scale-105 transition-all duration-200 cursor-pointer text-left
        focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950
      `}
      aria-label={`
        ${title}: ${value} ${
        trend ? `${trend.direction === 'up' ? 'up' : 'down'} ${trend.value}% ${trend.label}` : ''
      }
      `}
      type="button"
    >
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-medium text-slate-600 dark:text-slate-400">{title}</h3>
        {Icon && (
          <Icon className="w-5 h-5 text-slate-400 dark:text-slate-500 opacity-50" aria-hidden="true" />
        )}
      </div>

      <div className="flex items-baseline gap-2">
        <span className="text-4xl font-bold text-slate-900 dark:text-white">{value}</span>
      </div>

      {trend && (
        <div className={`flex items-center gap-1 mt-3 text-sm font-medium ${
          trend.direction === 'up'
            ? 'text-green-600 dark:text-green-400'
            : 'text-red-600 dark:text-red-400'
        }`}>
          {trend.direction === 'up' ? (
            <TrendingUp className="w-4 h-4" aria-hidden="true" />
          ) : (
            <TrendingDown className="w-4 h-4" aria-hidden="true" />
          )}
          <span><span className="sr-only">Trend: </span>{trend.value}% {trend.label}</span>
        </div>
      )}
    </button>
  );
});
