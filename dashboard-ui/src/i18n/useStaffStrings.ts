import { useMemo } from 'react';
import { staffStrings, type StaffStrings } from './staff';

/** The staff panel's strings in the current language (a language switch reloads the page, so this never changes). */
export function useStaffStrings(): StaffStrings {
  return useMemo(() => staffStrings(), []);
}
