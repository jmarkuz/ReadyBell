import { expect, test } from '@playwright/test';
import { signInAndWaitForDashboard } from './helpers';

test('dark theme switches on, survives a reload and switches back', async ({ page }) => {
  await signInAndWaitForDashboard(page);
  const html = page.locator('html');
  await expect(html).not.toHaveClass(/dark/);

  await page.getByRole('button', { name: 'Switch to dark theme' }).click();
  await expect(html).toHaveClass(/dark/);

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(html).toHaveClass(/dark/);

  await page.getByRole('button', { name: 'Switch to light theme' }).click();
  await expect(html).not.toHaveClass(/dark/);
});
