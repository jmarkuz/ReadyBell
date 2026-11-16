/** Customer-facing pages (opened from the QR code that staff hand over) that need no staff sign-in. */
export function isPublicCustomerPath(pathname: string): boolean {
  return pathname.startsWith('/track/');
}
