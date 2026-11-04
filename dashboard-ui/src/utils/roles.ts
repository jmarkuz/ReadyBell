export type Role = 'STAFF' | 'RESTAURANT_ADMIN' | 'PLATFORM_ADMIN';

export interface NavEntry {
  id: string;
  label: string;
  route: string;
}

/** Sidebar entries each role may use. */
export function navFor(role: Role | null | undefined): NavEntry[] {
  switch (role) {
    case 'PLATFORM_ADMIN':
      return [{ id: 'restaurants', label: 'Restaurants', route: '/restaurants' }];
    case 'RESTAURANT_ADMIN':
      return [
        { id: 'dashboard', label: 'Dashboard', route: '/' },
        { id: 'staff', label: 'Staff', route: '/staff' },
        { id: 'settings', label: 'Settings', route: '/settings' },
      ];
    default:
      return [{ id: 'dashboard', label: 'Dashboard', route: '/' }];
  }
}

/** Where a role lands after signing in. */
export function homeFor(role: Role | null | undefined): string {
  return role === 'PLATFORM_ADMIN' ? '/restaurants' : '/';
}
