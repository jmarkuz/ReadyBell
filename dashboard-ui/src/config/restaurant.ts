import { Coffee, type LucideIcon } from 'lucide-react';

/** Branding of the restaurant that owns this dashboard (shown in the header). */
export interface RestaurantBranding {
  name: string;
  Logo: LucideIcon;
}

// Shown until the restaurant's own profile (GET /api/restaurant) has loaded, and when it has none.
export const restaurantBranding: RestaurantBranding = {
  name: 'ReadyBell',
  Logo: Coffee,
};
