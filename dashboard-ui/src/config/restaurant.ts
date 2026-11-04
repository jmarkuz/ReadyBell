import { Coffee, type LucideIcon } from 'lucide-react';

/** Branding of the restaurant that owns this dashboard (shown in the header). */
export interface RestaurantBranding {
  name: string;
  Logo: LucideIcon;
}

// Sample branding until the backend exposes the restaurant profile.
export const restaurantBranding: RestaurantBranding = {
  name: 'Café Aurora',
  Logo: Coffee,
};
