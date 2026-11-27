import { staffStrings } from '@/i18n/staff';

export type Role = 'STAFF' | 'RESTAURANT_ADMIN' | 'PLATFORM_ADMIN';

export interface NavEntry {
  id: string;
  label: string;
  route: string;
}

/** Sidebar entries each role may use. */
export function navFor(role: Role | null | undefined): NavEntry[] {
  const t = staffStrings();
  switch (role) {
    case 'PLATFORM_ADMIN':
      return [{ id: 'restaurants', label: t.navRestaurants, route: '/restaurants' }];
    case 'RESTAURANT_ADMIN':
      return [
        { id: 'dashboard', label: t.navDashboard, route: '/' },
        { id: 'staff', label: t.navStaff, route: '/staff' },
        { id: 'settings', label: t.navSettings, route: '/settings' },
      ];
    default:
      return [{ id: 'dashboard', label: t.navDashboard, route: '/' }];
  }
}

/** Where a role lands after signing in. */
export function homeFor(role: Role | null | undefined): string {
  return role === 'PLATFORM_ADMIN' ? '/restaurants' : '/';
}
