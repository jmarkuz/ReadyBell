/** Customer-facing pages (opened from the receipt QR or the counter QR) that need no staff sign-in. */
export function isPublicCustomerPath(pathname: string): boolean {
  return pathname.startsWith('/track/') || pathname.startsWith('/r/');
}
