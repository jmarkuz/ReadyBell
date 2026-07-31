import React from 'react';
import { OrderStatus } from '@/types';
import { Clock, Flame, CheckCircle, Archive, XCircle } from 'lucide-react';

interface OrderStatusBadgeProps {
  status: OrderStatus;
}

/**
 * Order Status Badge Component
 * 
 * Displays a color-coded status badge with:
 * - Appropriate icon for status
 * - Label text
 * - Theme-aware colors (light/dark mode)
 * 
 * Status mapping:
 * - CREATED: blue (New Order) with Clock icon
 * - PREPARING: orange (Preparing) with Flame icon
 * - READY: green (Ready for Pickup) with CheckCircle icon
 * - COMPLETED: gray (Completed) with Archive icon
 * - CANCELLED: red (Cancelled) with XCircle icon
 */
export default function OrderStatusBadge({ status }: OrderStatusBadgeProps) {
  const statusConfig: Record<OrderStatus, {
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    bgColor: string;
    textColor: string;
  }> = {
    CREATED: {
      label: 'New Order',
      icon: Clock,
      bgColor: 'bg-blue-50 dark:bg-blue-900/30',
      textColor: 'text-blue-700 dark:text-blue-300',
    },
    PREPARING: {
      label: 'Preparing',
      icon: Flame,
      bgColor: 'bg-orange-50 dark:bg-orange-900/30',
      textColor: 'text-orange-700 dark:text-orange-300',
    },
    READY: {
      label: 'Ready for Pickup',
      icon: CheckCircle,
      bgColor: 'bg-green-50 dark:bg-green-900/30',
      textColor: 'text-green-700 dark:text-green-300',
    },
    COMPLETED: {
      label: 'Completed',
      icon: Archive,
      bgColor: 'bg-gray-50 dark:bg-gray-900/30',
      textColor: 'text-gray-700 dark:text-gray-300',
    },
    CANCELLED: {
      label: 'Cancelled',
      icon: XCircle,
      bgColor: 'bg-red-50 dark:bg-red-900/30',
      textColor: 'text-red-700 dark:text-red-300',
    },
  };

  const config = statusConfig[status];
  const Icon = config.icon;

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium ${config.bgColor} ${config.textColor}`}
      role="status"
      aria-label={config.label}
    >
      <Icon className="w-3.5 h-3.5" />
      {config.label}
    </span>
  );
}
