import { expect, test } from '@playwright/test';

// The app runs in Dev mode with GOOGLEAUTH_DEV_BYPASS=true. Any request to Google fails the test,
// proving the bypass never contacts it.
test.beforeEach(async ({ page }) => {
  await page.route('https://accounts.google.com/**', (route) => {
    throw new Error(`Unexpected request to Google: ${route.request().url()}`);
  });
});

test('serves a protected page without logging in, as the fake dev user', async ({ page }) => {
  await page.goto('/authenticated');

  await expect(page).toHaveURL('/authenticated');
  await expect(page.locator('b')).toHaveText('dev-user');
});

test('skips the Google group check', async ({ page }) => {
  const response = await page.goto('/authenticatedGroup');

  expect(response?.status()).toBe(200);
  await expect(page).toHaveURL('/authenticatedGroup');
  await expect(page.getByRole('heading', { name: 'Authorised' })).toBeVisible();
});

test('does not redirect an unauthenticated request', async ({ request }) => {
  const response = await request.get('/authenticated', { maxRedirects: 0 });

  expect(response.status()).toBe(200);
});
