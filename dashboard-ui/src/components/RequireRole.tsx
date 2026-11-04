import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { apiClient } from '@/utils/api';
import { homeFor, type Role } from '@/utils/roles';

/** Renders the page only for the listed roles; anyone else is sent to their own home page. */
export default function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const role = apiClient.getUserProfile()?.role ?? null;
  if (role && roles.includes(role)) return <>{children}</>;
  return <Navigate to={homeFor(role)} replace />;
}
