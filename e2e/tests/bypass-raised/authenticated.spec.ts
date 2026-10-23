import { expect, test } from '@playwright/test';

test.describe('authenticated page', () => {
  test('is reachable without logging in and shows the dev user', async ({ page }) => {
    const response = await page.goto('/authenticated');

    expect(response?.status()).toBe(200);
    await expect(page).toHaveURL('/authenticated');
    await expect(page).toHaveTitle('Authorised');
    await expect(page.getByRole('heading', { name: 'Authorised' })).toBeVisible();
    await expect(page.locator('b')).toHaveText('dev-user');
  });

  test('shows an expiry time and no avatar', async ({ page }) => {
    await page.goto('/authenticated');

    await expect(page.getByText(/The expiry time is \+?\d+-\d{2}-\d{2}T/)).toBeVisible();
    await expect(page.getByRole('img')).toHaveCount(0);
  });

  test('links to the login page and logout', async ({ page }) => {
    await page.goto('/authenticated');

    await expect(page.getByRole('link', { name: 'login page' })).toHaveAttribute('href', '/login');
    await expect(page.getByRole('link', { name: 'logout' })).toHaveAttribute('href', '/logout');
  });
});
