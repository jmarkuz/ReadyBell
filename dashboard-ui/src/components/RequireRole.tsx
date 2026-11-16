import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { apiClient } from '@/utils/api';
import { homeFor, type Role } from '@/utils/roles';

/** Renders the page only for the listed roles; anyone else is sent to their own home page. */
export default function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const role = apiClient.getUserProfile()?.role ?? null;
  const { pathname } = useLocation();
  if (role && roles.includes(role)) return <>{children}</>;
  // the customer display runs on its own screen: after signing in it returns there
  if (!role && pathname === '/display') return <Navigate to="/login?next=/display" replace />;
  return <Navigate to={homeFor(role)} replace />;
}
