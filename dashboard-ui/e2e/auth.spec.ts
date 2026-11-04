import { expect, test } from '@playwright/test';
import { signIn, signInAndWaitForDashboard } from './helpers';

test.describe('sign in', () => {
  test('staff sign in and land on the dashboard', async ({ page }) => {
    await signInAndWaitForDashboard(page);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('button', { name: 'New order', exact: true })).toBeVisible();
  });

  test('wrong password shows an error and stays on the login page', async ({ page }) => {
    await signIn(page, { username: 'e2e-nobody', password: 'wrong-password' });

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('the dashboard is not reachable without signing in', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });
});
