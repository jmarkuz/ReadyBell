import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { apiClient } from '@/utils/api';
import { restaurantBranding } from '@/config/restaurant';
import { RestaurantContext } from './restaurantContext';

/** Loads the signed-in user's restaurant so the dashboard can show its real name. */
export function RestaurantProvider({ children }: { children: ReactNode }) {
  const [name, setName] = useState(restaurantBranding.name);
  const [identifier, setIdentifier] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    // a platform operator belongs to no restaurant
    if (apiClient.getUserProfile()?.role === 'PLATFORM_ADMIN') return;
    try {
      const profile = await apiClient.getRestaurantProfile();
      if (profile.name) setName(profile.name);
      setIdentifier(profile.identifier);
    } catch {
      /* keep the default branding: e.g. a seed user without a restaurant record */
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const value = useMemo(() => ({ name, identifier, refresh }), [name, identifier, refresh]);
  return <RestaurantContext.Provider value={value}>{children}</RestaurantContext.Provider>;
}
