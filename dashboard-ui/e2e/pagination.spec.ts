import { expect, test } from '@playwright/test';
import { apiToken, signInAndWaitForDashboard } from './helpers';

test('incoming orders are paged by the server: 10 rows, Next and Previous', async ({ page, request }) => {
  // 25 orders entered through the normal API, on top of whatever earlier scenarios created
  const token = await apiToken(request);
  for (let i = 1; i <= 25; i++) {
    const created = await request.post('/api/orders', {
      headers: { Authorization: `Bearer ${token}` },
      data: { customerName: `Paging Customer ${i}`, estimatedMinutes: 10, items: [{ name: 'Latte', quantity: 1 }] },
    });
    expect(created.ok()).toBeTruthy();
  }

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
