import { createContext, useContext } from 'react';
import { restaurantBranding } from '@/config/restaurant';

export interface RestaurantState {
  name: string;
  identifier: string | null;
  refresh: () => Promise<void>;
}

export const RestaurantContext = createContext<RestaurantState>({
  name: restaurantBranding.name,
  identifier: null,
  refresh: async () => {},
});

export function useRestaurant(): RestaurantState {
  return useContext(RestaurantContext);
}
