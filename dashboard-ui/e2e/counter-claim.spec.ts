import { expect, test, type Page } from '@playwright/test';
import { signInAndWaitForDashboard } from './helpers';

async function createOrder(page: Page): Promise<string> {
  await page.getByRole('button', { name: 'New order', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel(/Customer name/).fill('Counter Customer');
  await dialog.getByLabel('Item 1 name').fill('Flat white');
  await dialog.getByRole('button', { name: 'Create order' }).click();
  await expect(dialog.getByRole('heading', { name: 'Order created' })).toBeVisible();
  const orderNumber = (await dialog.locator('p.tracking-wide').innerText()).trim();
  await dialog.getByRole('button', { name: 'Close' }).click();
  return orderNumber;
}

/**
 * Counter QR: the restaurant shows one QR at the pickup area; the customer types the number from the receipt,
 * lands on the tracking page and sees READY when staff mark the order.
 */
test('counter QR -> receipt number -> live tracking until ready', async ({ page, browser }) => {
  await signInAndWaitForDashboard(page);

  // The restaurant admin finds the counter QR in Settings
  await page.getByRole('link', { name: 'Settings' }).first().click();
  await expect(page.getByRole('heading', { name: 'Counter QR code' })).toBeVisible();
  const counterUrl = (await page.locator('section[aria-labelledby="counter-qr"] p.break-all').innerText()).trim();
  expect(counterUrl).toMatch(/\/r\/dev-restaurant$/);

  await page.getByRole('link', { name: 'Dashboard' }).first().click();
  const orderNumber = await createOrder(page);

  const customerContext = await browser.newContext();
  const customer = await customerContext.newPage();
  try {
    await customer.goto(counterUrl);
    await expect(customer.getByRole('heading', { name: 'Find your order' })).toBeVisible();

    // A number that does not exist, and the one typed wrongly, give the same generic answer
    await customer.getByLabel('Order number').fill('999999');
    await customer.getByRole('button', { name: 'Show my order' }).click();
    await expect(customer.getByRole('alert')).toContainText('could not find an order with this number');

    // The right number opens the tracking page of that order
    await customer.getByLabel('Order number').fill(orderNumber);
    await customer.getByRole('button', { name: 'Show my order' }).click();
    await expect(customer).toHaveURL(/\/track\/[0-9a-f-]{36}$/);
    await expect(customer.getByText(orderNumber, { exact: true })).toBeVisible();
    await expect(customer.getByText('Received')).toBeVisible();

    // Staff mark it ready: the customer's page flips by itself
    await page.getByRole('button', { name: new RegExp(`Update status for order .*${orderNumber.replace('#', '#?')}`) }).first().click();
    await page.getByRole('menuitem', { name: 'Start preparing' }).click();
    await page.getByRole('button', { name: new RegExp(`Update status for order .*${orderNumber.replace('#', '#?')}`) }).first().click();
    await page.getByRole('menuitem', { name: 'Mark ready for pickup' }).click();
    await expect(customer.getByText('Your order is ready for pickup!')).toBeVisible({ timeout: 20_000 });
  } finally {
    await customerContext.close();
  }

  // A second customer cannot claim the same number again
  const stranger = await browser.newContext();
  const strangerPage = await stranger.newPage();
  try {
    await strangerPage.goto(counterUrl);
    await strangerPage.getByLabel('Order number').fill(orderNumber);
    await strangerPage.getByRole('button', { name: 'Show my order' }).click();
    await expect(strangerPage.getByRole('alert')).toContainText('could not find an order with this number');
  } finally {
    await stranger.close();
  }
});

test('an unknown counter QR says so', async ({ page }) => {
  await page.goto('/r/no-such-restaurant');
  await expect(page.getByRole('heading', { name: 'This QR code is not valid' })).toBeVisible();
});
