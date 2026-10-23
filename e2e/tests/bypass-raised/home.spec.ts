import { expect, test } from '@playwright/test';

test.describe('home page', () => {
  test('renders and links to the other pages', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle('Home');
    await expect(page.getByRole('heading', { name: 'Home page' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'login page' })).toHaveAttribute('href', '/login');
    await expect(page.getByRole('link', { name: 'page that requires authentication' })).toHaveAttribute(
      'href',
      '/authenticated',
    );
    await expect(page.getByRole('link', { name: 'logout' })).toHaveAttribute('href', '/logout');
  });

  test('navigates to the login page', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'login page' }).click();

    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('heading', { name: 'Login Page' })).toBeVisible();
  });

  test('navigates to the authenticated page', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'page that requires authentication' }).click();

    await expect(page).toHaveURL('/authenticated');
    await expect(page.getByRole('heading', { name: 'Authorised' })).toBeVisible();
  });
});
