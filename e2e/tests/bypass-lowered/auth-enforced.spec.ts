import { expect, test } from '@playwright/test';

// The app runs in Dev mode with GOOGLEAUTH_DEV_BYPASS=false, so real Google authentication is required.
// Google itself is stubbed: we only need to prove the app sends the browser there instead of serving the page.
test.beforeEach(async ({ page }) => {
  await page.route('https://accounts.google.com/**', (route) =>
    route.fulfill({ contentType: 'text/html', body: '<h1>Stubbed Google sign-in</h1>' }),
  );
});

for (const path of ['/authenticated', '/authenticatedGroup']) {
  test.describe(path, () => {
    test('starts the Google auth flow instead of serving the page', async ({ page }) => {
      await page.goto(path);

      await expect(page).toHaveURL(/^https:\/\/accounts\.google\.com\//);
    });

    test('is not served to an unauthenticated request', async ({ request }) => {
      const response = await request.get(path, { maxRedirects: 0 });

      expect(response.status()).toBe(303);
      expect(response.headers()['location']).toBe('/loginAction');
    });
  });
}

test('the Google redirect carries an OAuth state and the configured client', async ({ request }) => {
  const response = await request.get('/loginAction', { maxRedirects: 0 });
  const location = new URL(response.headers()['location']);

  expect(location.origin).toBe('https://accounts.google.com');
  expect(location.searchParams.get('client_id')).toBeTruthy();
  expect(location.searchParams.get('state')).toBeTruthy();
});

test('a forged OAuth callback does not log the user in', async ({ page }) => {
  await page.goto('/oauth2callback?code=forged&state=forged');
  await page.goto('/authenticated');

  await expect(page).toHaveURL(/^https:\/\/accounts\.google\.com\//);
});
