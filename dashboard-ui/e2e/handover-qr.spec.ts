import { expect, test, type Page } from '@playwright/test';
import { STAFF, signInAndWaitForDashboard } from './helpers';

async function createOrder(page: Page): Promise<{ orderNumber: string; trackingUrl: string }> {
  await page.getByRole('button', { name: 'New order', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel(/Customer name/).fill('Handover Customer');
  await dialog.getByLabel('Item 1 name').fill('Flat white');
  await dialog.getByRole('button', { name: 'Create order' }).click();
  await expect(dialog.getByRole('heading', { name: 'Order created' })).toBeVisible();
  const orderNumber = (await dialog.locator('p.tracking-wide').innerText()).trim();
  const trackingUrl = (await dialog.locator('p.break-all').innerText()).trim();
  return { orderNumber, trackingUrl };
}

/**
 * QR handed over in person: the customer scans the code staff show, the staff screen notices at once, the code
 * disappears (nobody behind in the queue can scan it) and staff can bring it back for the same order.
 */
test('scanning the QR connects the customer and hides the code', async ({ page, browser }) => {
  await signInAndWaitForDashboard(page);
  const { orderNumber, trackingUrl } = await createOrder(page);
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('img', { name: `QR code for order ${orderNumber}` })).toBeVisible();

  const customerContext = await browser.newContext();
  const customer = await customerContext.newPage();
  try {
    await customer.goto(trackingUrl);
    await expect(customer.getByText(orderNumber, { exact: true })).toBeVisible();

    // The staff screen learns about it without a reload
    await expect(dialog.getByText('Customer connected')).toBeVisible({ timeout: 20_000 });
    await expect(dialog.getByRole('img', { name: `QR code for order ${orderNumber}` })).toHaveCount(0);

    // The table shows it too, and the code can be brought back for this order
    await dialog.getByRole('button', { name: 'Close' }).click();
    const row = page.getByRole('row').filter({ hasText: orderNumber });
    await expect(row.getByText('Connected')).toBeVisible();
    await row.getByRole('button', { name: `Show QR code for order ${orderNumber}` }).click();
    await expect(page.getByRole('dialog').getByText('Customer connected')).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: 'Show QR code again' }).click();
    await expect(page.getByRole('dialog').getByRole('img', { name: `QR code for order ${orderNumber}` })).toBeVisible();
  } finally {
    await customerContext.close();
  }
});

test('an order not yet connected can show its QR from the list, and the customer display follows it', async ({ page, browser }) => {
  await signInAndWaitForDashboard(page);

  // The display runs on its own screen with its own sign-in, and returns to /display after it
  const displayContext = await browser.newContext();
  const display = await displayContext.newPage();
  await display.goto('/display');
  await expect(display).toHaveURL(/\/login\?next=\/display$/);
  await display.getByLabel('Username').fill(STAFF.username);
  await display.getByLabel('Password').fill(STAFF.password);
  await display.getByRole('button', { name: 'Sign in' }).click();
  await expect(display).toHaveURL(/\/display$/);
  await expect(display.getByText(/Your order will appear here/)).toBeVisible();

  const { orderNumber, trackingUrl } = await createOrder(page);
  await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click();

  await expect(display.getByRole('img', { name: `QR code for order ${orderNumber}` })).toBeVisible({ timeout: 20_000 });
  // only the number and the code: nothing about the customer
  await expect(display.getByText('Handover Customer')).toHaveCount(0);

  const customerContext = await browser.newContext();
  const customer = await customerContext.newPage();
  try {
    await customer.goto(trackingUrl);
    await expect(display.getByText('You are connected')).toBeVisible({ timeout: 20_000 });
    await expect(display.getByRole('img', { name: `QR code for order ${orderNumber}` })).toHaveCount(0);
  } finally {
    await customerContext.close();
    await displayContext.close();
  }
});

test('the customer display needs a staff sign-in', async ({ page }) => {
  await page.goto('/display');
  await expect(page).toHaveURL(/\/login/);
});
