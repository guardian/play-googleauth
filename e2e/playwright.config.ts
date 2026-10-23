import { defineConfig, devices } from '@playwright/test';

const loweredPort = Number(process.env.E2E_PORT ?? '9000');
const raisedPort = loweredPort + 1;

const server = (port: number, bypass: boolean) => ({
  command: `node scripts/start-example-app.js ${port} ${bypass}`,
  url: `http://localhost:${port}`,
  reuseExistingServer: !process.env.CI,
  timeout: 120_000,
  stdout: 'pipe' as const,
});

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: { trace: 'on-first-retry' },
  projects: [
    {
      name: 'bypass-lowered',
      testDir: './tests/bypass-lowered',
      use: { ...devices['Desktop Chrome'], baseURL: `http://localhost:${loweredPort}` },
    },
    {
      name: 'bypass-raised',
      testDir: './tests/bypass-raised',
      use: { ...devices['Desktop Chrome'], baseURL: `http://localhost:${raisedPort}` },
    },
  ],
  webServer: [server(loweredPort, false), server(raisedPort, true)],
});
