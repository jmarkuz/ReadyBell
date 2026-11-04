import { expect, test } from '@playwright/test';
import { apiToken, signInAndWaitForDashboard } from './helpers';

test('incoming orders are paged by the server: 10 rows, Next and Previous', async ({ page, request }) => {
  // Seed through the dev-profile endpoint: 25 orders on top of whatever earlier scenarios created
  const token = await apiToken(request);
  const seeded = await request.post('/api/dev/test-data?count=25', { headers: { Authorization: `Bearer ${token}` } });
  expect(seeded.ok()).toBeTruthy();

  await signInAndWaitForDashboard(page);

  const rows = page.getByRole('table', { name: 'Incoming orders list' }).locator('tbody tr');
  const pagination = page.getByRole('region', { name: 'Table pagination' });
  await expect(pagination).toBeVisible();
  await expect(pagination).toContainText('Page 1 of');
  await expect(rows).toHaveCount(10);

  await page.getByRole('button', { name: 'Go to next page' }).click();
  await expect(pagination).toContainText('Page 2 of');
  await expect(rows).toHaveCount(10);

  await page.getByRole('button', { name: 'Go to previous page' }).click();
  await expect(pagination).toContainText('Page 1 of');
});
