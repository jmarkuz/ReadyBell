import { expect, type APIRequestContext, type Page } from '@playwright/test';

/** Staff accounts of the dev profile (application-dev.yml). */
export const STAFF = { username: 'admin', password: 'admin' };

export async function signIn(page: Page, { username, password } = STAFF) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

export async function signInAndWaitForDashboard(page: Page) {
  await signIn(page);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

/** A staff JWT for API calls made by the test itself (seeding). */
export async function apiToken(request: APIRequestContext): Promise<string> {
  const res = await request.post('/api/login', { data: STAFF });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).token as string;
}
