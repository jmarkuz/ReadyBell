import { expect, test } from '@playwright/test';
import { signInAndWaitForDashboard } from './helpers';

/**
 * The product's main promise: staff take an order, the customer follows it from the receipt QR link, and the
 * customer's page shows READY the moment staff mark it, without reloading.
 */
test('order -> QR link -> customer follows it live until ready', async ({ page, browser }) => {
  await signInAndWaitForDashboard(page);

  // Staff create an order
  await page.getByRole('button', { name: 'New order', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel(/Customer name/).fill('E2E Customer');
  await dialog.getByLabel('Item 1 name').fill('Latte');
  await dialog.getByRole('button', { name: 'Create order' }).click();

  await expect(dialog.getByRole('heading', { name: 'Order created' })).toBeVisible();
  const orderNumber = (await dialog.locator('p.tracking-wide').innerText()).trim();
  await expect(dialog.getByRole('img', { name: `QR code for order ${orderNumber}` })).toBeVisible();
  const trackingUrl = (await dialog.locator('p.break-all').innerText()).trim();
  expect(trackingUrl).toMatch(/\/track\/[0-9a-f-]{36}$/);
  await dialog.getByRole('button', { name: 'Close' }).click();

  // The customer scans the QR code: a separate browser, no sign-in
  const customerContext = await browser.newContext();
  const customer = await customerContext.newPage();
  try {
    await customer.goto(trackingUrl);
    await expect(customer.getByText(orderNumber)).toBeVisible();
    await expect(customer.getByText('Received')).toBeVisible();

    // Staff start preparing
    await page.getByRole('button', { name: new RegExp(`Update status for order .*${orderNumber.replace('#', '#?')}`) }).first().click();
    await page.getByRole('menuitem', { name: 'Start preparing' }).click();
    await expect(customer.getByText('Being prepared')).toBeVisible({ timeout: 20_000 });

    // Staff mark it ready: the customer's open page flips by itself
    await page.getByRole('button', { name: new RegExp(`Update status for order .*${orderNumber.replace('#', '#?')}`) }).first().click();
    await page.getByRole('menuitem', { name: 'Mark ready for pickup' }).click();
    await expect(customer.getByText('Your order is ready for pickup!')).toBeVisible({ timeout: 20_000 });
    await expect(customer.getByText('Ready for pickup!', { exact: true })).toBeVisible();
  } finally {
    await customerContext.close();
  }
});

test('an unknown tracking link says so', async ({ page }) => {
  await page.goto('/track/00000000-0000-0000-0000-00000000dead');
  await expect(page.getByRole('heading', { name: 'This link is not valid' })).toBeVisible();
});
