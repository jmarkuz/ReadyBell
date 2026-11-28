import { memo } from 'react';
import OrderSummaryCard from './OrderSummaryCard';
import { OrderMetrics } from '@/types';
import { ShoppingCart, Clock, Zap, CheckCircle } from 'lucide-react';
import { useStaffStrings } from '@/i18n/useStaffStrings';

interface OrderMetricsGridProps {
  metrics: OrderMetrics;
  isLoading?: boolean;
  onCardClick?: (metric: string) => void;
}

/** Order Metrics Grid Component */
export default memo(function OrderMetricsGrid({
  metrics,
  isLoading = false,
  onCardClick,
}: OrderMetricsGridProps) {
  const t = useStaffStrings();
  const cards = [
    {
      id: 'total',
      title: t.metricTotal,
      value: metrics.totalOrders,
      icon: ShoppingCart,
    },
    {
      id: 'new',
      title: t.metricNew,
      value: metrics.newOrders,
      icon: Clock,
    },
    {
      id: 'preparing',
      title: t.statusPreparing,
      value: metrics.preparing,
      icon: Zap,
    },
    {
      id: 'ready',
      title: t.statusReady,
      value: metrics.readyForPickup,
      icon: CheckCircle,
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
          isLoading={isLoading}
          onClick={() => onCardClick?.(card.id)}
        />
      ))}
    </div>
  );
});
