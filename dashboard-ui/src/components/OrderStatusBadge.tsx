import React, { memo } from 'react';
import { OrderStatus } from '@/types';
import { Clock, Flame, CheckCircle, Archive, XCircle } from 'lucide-react';
import { useStaffStrings } from '@/i18n/useStaffStrings';

interface OrderStatusBadgeProps {
  status: OrderStatus;
}

/** Order Status Badge Component */
export default memo(function OrderStatusBadge({ status }: OrderStatusBadgeProps) {
  const t = useStaffStrings();
  const statusConfig: Record<OrderStatus, {
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    bgColor: string;
    textColor: string;
  }> = {
    CREATED: {
      label: t.statusCreated,
      icon: Clock,
      bgColor: 'bg-blue-50 dark:bg-blue-900/30',
      textColor: 'text-status-info', // Use custom status color
    },
    PREPARING: {
      label: t.statusPreparing,
      icon: Flame,
      bgColor: 'bg-orange-50 dark:bg-orange-900/30',
      textColor: 'text-status-warning', // Use custom status color
    },
    READY: {
      label: t.statusReady,
      icon: CheckCircle,
      bgColor: 'bg-green-50 dark:bg-green-900/30',
      textColor: 'text-status-success', // Use custom status color
    },
    COMPLETED: {
      label: t.statusCompleted,
      icon: Archive,
      bgColor: 'bg-gray-50 dark:bg-gray-900/30',
      textColor: 'text-gray-700 dark:text-gray-300',
    },
    CANCELLED: {
      label: t.statusCancelled,
      icon: XCircle,
      bgColor: 'bg-red-50 dark:bg-red-900/30',
      textColor: 'text-status-error', // Use custom status color
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
      <Icon className="w-3.5 h-3.5" aria-hidden="true" />
      <span>{config.label}</span>
    </span>
  );
});
