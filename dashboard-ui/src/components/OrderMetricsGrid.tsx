import OrderSummaryCard from './OrderSummaryCard';
import { OrderMetrics } from '@/types';
import { ShoppingCart, Clock, Zap, CheckCircle } from 'lucide-react';

interface OrderMetricsGridProps {
  metrics: OrderMetrics;
  isLoading?: boolean;
  onCardClick?: (metric: string) => void;
}

/**
 * Order Metrics Grid Component
 * 
 * Displays 4 order summary cards in a responsive grid:
 * - Total Orders
 * - New Orders (CREATED status)
 * - Preparing (PREPARING status)
 * - Ready for Pickup (READY status)
 * 
 * Responsive:
 * - Desktop (1024px+): 4 columns
 * - Tablet (768px-1023px): 2 columns
 * - Mobile (<768px): 1 column (stacked)
 */
export default function OrderMetricsGrid({
  metrics,
  isLoading = false,
  onCardClick,
}: OrderMetricsGridProps) {
  const cards = [
    {
      id: 'total',
      title: 'Total Orders',
      value: metrics.totalOrders,
      icon: ShoppingCart,
      trend: { value: 12.5, direction: 'up' as const, label: 'vs. last hour' },
    },
    {
      id: 'new',
      title: 'New Orders',
      value: metrics.newOrders,
      icon: Clock,
      trend: { value: 5.2, direction: 'up' as const, label: 'vs. last hour' },
    },
    {
      id: 'preparing',
      title: 'Preparing',
      value: metrics.preparing,
      icon: Zap,
      trend: { value: 2.1, direction: 'down' as const, label: 'vs. last hour' },
    },
    {
      id: 'ready',
      title: 'Ready for Pickup',
      value: metrics.readyForPickup,
      icon: CheckCircle,
      trend: { value: 8.3, direction: 'up' as const, label: 'vs. last hour' },
    },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      {cards.map((card) => (
        <OrderSummaryCard
          key={card.id}
          title={card.title}
          value={card.value}
          icon={card.icon}
          trend={card.trend}
          isLoading={isLoading}
          onClick={() => onCardClick?.(card.id)}
        />
      ))}
    </div>
  );
}
