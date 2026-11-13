import { expect, test } from '@playwright/test';
import { signInAndWaitForDashboard } from './helpers';

/**
 * A second staff screen (kitchen tablet) learns about an order entered at the counter without reloading, and
 * announces it; the screen the order was entered on does not announce its own order.
 */
test('an order entered at one screen appears and is announced on the other', async ({ page, browser }) => {
  const kitchenContext = await browser.newContext();
  const kitchen = await kitchenContext.newPage();
  try {
    await signInAndWaitForDashboard(kitchen);
    const sound = kitchen.getByRole('button', { name: 'New order sound' });
    await expect(sound).toHaveAttribute('aria-pressed', 'false');   // off until staff turn it on
    await sound.click();
    await expect(sound).toHaveAttribute('aria-pressed', 'true');
    await expect(kitchen.getByText('Live')).toBeVisible();          // the live stream is open

    // The counter enters an order
    await signInAndWaitForDashboard(page);
    await page.getByRole('button', { name: 'New order', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/Customer name/).fill('Alert Customer');
    await dialog.getByLabel('Item 1 name').fill('Espresso');
    await dialog.getByRole('button', { name: 'Create order' }).click();
    await expect(dialog.getByRole('heading', { name: 'Order created' })).toBeVisible();
    const orderNumber = (await dialog.locator('p.tracking-wide').innerText()).trim();
    await dialog.getByRole('button', { name: 'Close' }).click();

    // The kitchen shows it live, with a screen-reader announcement
    await expect(kitchen.getByRole('cell', { name: orderNumber, exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(kitchen.locator('[aria-live="polite"]', { hasText: `New order ${orderNumber}` })).toHaveCount(1, { timeout: 15_000 });

    // The counter, where the order was entered, stays quiet about it (the alert has a short grace period)
    await page.waitForTimeout(2500);
    await expect(page.locator('[aria-live="polite"]', { hasText: 'New order' })).toHaveCount(0);
  } finally {
    await kitchenContext.close();
  }
});
