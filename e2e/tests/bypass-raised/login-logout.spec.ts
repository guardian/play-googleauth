import { expect, test } from '@playwright/test';

test.describe('login page', () => {
  test('renders the login button without an error', async ({ page }) => {
    await page.goto('/login');

    await expect(page).toHaveTitle('Login');
    await expect(page.getByRole('heading', { name: 'Login Page' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Log In' })).toBeVisible();
    await expect(page.getByText('The error was:')).toHaveCount(0);
  });

  test('submits the login form to the login action', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('form')).toHaveAttribute('action', '/loginAction');
    await expect(page.locator('form')).toHaveAttribute('method', 'get');
  });

  test('links to the authenticated page', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('link', { name: 'page that requires authentication' }).click();

    await expect(page).toHaveURL('/authenticated');
  });
});

test.describe('logout', () => {
  test('redirects to the home page', async ({ page }) => {
    await page.goto('/authenticated');
    await page.getByRole('link', { name: 'logout' }).click();

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Home page' })).toBeVisible();
  });

  test('keeps the authenticated page reachable afterwards', async ({ page }) => {
    await page.goto('/logout');
    await page.goto('/authenticated');

    await expect(page).toHaveURL('/authenticated');
    await expect(page.locator('b')).toHaveText('dev-user');
  });
});

test.describe('unknown routes', () => {
  test('return a 404', async ({ page }) => {
    const response = await page.goto('/does-not-exist');

    expect(response?.status()).toBe(404);
  });
});
