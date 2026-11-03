import { apiClient } from './api';

/** Clears the stored token and profile and returns to the login page (full reload drops in-memory state). */
export function signOut(): void {
  apiClient.logout();
  window.location.href = '/login';
}
