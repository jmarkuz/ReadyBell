import QRCode from 'qrcode';

/** Public origin customers' phones can reach (set VITE_PUBLIC_URL when the dashboard runs on localhost). */
export function publicOrigin(): string {
  return (import.meta.env.VITE_PUBLIC_URL as string | undefined)?.replace(/\/$/, '') || window.location.origin;
}

export function trackingUrl(sessionId: string): string {
  return `${publicOrigin()}/track/${sessionId}`;
}

export function qrDataUrl(text: string, width = 320): Promise<string> {
  return QRCode.toDataURL(text, { width, margin: 1, errorCorrectionLevel: 'M', color: { dark: '#292524', light: '#ffffff' } });
}

const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);

/** Opens a print-ready receipt slip (restaurant, order number, QR) in a new window. */
export function printReceipt(opts: { restaurant: string; orderNumber: string; qr: string; url: string }): void {
  const w = window.open('', '_blank', 'width=360,height=520');
  if (!w) return;
  w.document.write(`<!doctype html><title>${esc(opts.orderNumber)}</title>
<style>body{font-family:system-ui,sans-serif;text-align:center;margin:16px;color:#0c0a09}
h1{font-size:20px;margin:0 0 4px}.n{font-size:34px;font-weight:700;letter-spacing:.05em;margin:8px 0}
img{width:220px;height:220px}p{font-size:13px;margin:6px 0}small{color:#555;word-break:break-all}</style>
<h1>${esc(opts.restaurant)}</h1><div class="n">${esc(opts.orderNumber)}</div>
<img src="${opts.qr}" alt="QR code"><p>Scan to follow your order and get a notification when it is ready.</p>
<small>${esc(opts.url)}</small>`);
  w.document.close();
  // no inline script: the page's Content-Security-Policy would block it
  w.focus();
  setTimeout(() => w.print(), 300);
}
