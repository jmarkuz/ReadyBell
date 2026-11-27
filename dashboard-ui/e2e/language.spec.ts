import { expect, test } from '@playwright/test';
import { STAFF } from './helpers';

test.describe('Ukrainian browser', () => {
  test.use({ locale: 'uk-UA' });

  test('the sign-in and the dashboard are in Ukrainian', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByLabel('Ресторан')).toBeVisible();
    await page.getByLabel('Ресторан').fill(STAFF.restaurant);
    await page.getByLabel('Ім’я користувача').fill(STAFF.username);
    await page.getByLabel('Пароль').fill(STAFF.password);
    await page.getByRole('button', { name: 'Увійти' }).click();

    await expect(page.getByRole('heading', { name: 'Панель', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Нове замовлення', exact: true })).toBeVisible();
  });

  test('staff can switch the panel to English and the choice is remembered', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Ресторан').fill(STAFF.restaurant);
    await page.getByLabel('Ім’я користувача').fill(STAFF.username);
    await page.getByLabel('Пароль').fill(STAFF.password);
    await page.getByRole('button', { name: 'Увійти' }).click();
    await expect(page.getByRole('heading', { name: 'Панель', exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Перемкнути на англійську' }).click();

    await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();   // still English after a reload
  });
});
