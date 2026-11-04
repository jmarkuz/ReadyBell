/** Customer-facing pages (opened from the receipt QR code) that need no staff sign-in. */
export function isPublicCustomerPath(pathname: string): boolean {
  return pathname.startsWith('/track/');
}
