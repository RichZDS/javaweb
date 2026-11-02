import { defineConfig, devices } from '@playwright/test';

process.env.PLAYWRIGHT_BROWSERS_PATH ||= '/data/ms-playwright';
export default defineConfig({
  testDir: './ui', testMatch: '**/*.spec.mjs',
  fullyParallel: false, workers: 1, retries: 0, timeout: 180000,
  expect: { timeout: 10000 },
  outputDir: './artifacts/ui',
  reporter: [['list'], ['json', { outputFile: './artifacts/ui-results.json' }]],
  use: { screenshot: 'only-on-failure', trace: 'off', video: 'off' },
  projects: [
    { name: 'app-iphone13', testMatch: '**/app.spec.mjs', use: { ...devices['iPhone 13'], browserName: 'chromium', baseURL: process.env.APP_URL || 'http://127.0.0.1:4173' } },
    { name: 'admin-desktop', testMatch: '**/admin.spec.mjs', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 }, baseURL: process.env.ADMIN_URL || 'http://127.0.0.1:4174' } },
  ],
});
