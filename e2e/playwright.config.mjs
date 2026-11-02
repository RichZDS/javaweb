import { defineConfig, devices } from '@playwright/test';
import { APP_URL, ADMIN_URL } from './lib/stack-urls.mjs';

process.env.PLAYWRIGHT_BROWSERS_PATH ||= '/data/ms-playwright';
const appOnly = ['m1', 'm2', 'pm', 'settings'].includes(process.env.QA_SCOPE);
const adminOnly = process.env.QA_SCOPE === 'm3';
export default defineConfig({
  testDir: './ui', testMatch: '**/*.spec.mjs',
  fullyParallel: false, workers: 1, retries: 0, timeout: 180000,
  expect: { timeout: 10000 },
  outputDir: './artifacts/ui',
  reporter: [['list'], ['json', { outputFile: './artifacts/ui-results.json' }]],
  use: { screenshot: 'only-on-failure', trace: 'off', video: 'off', actionTimeout: 15000, navigationTimeout: 20000 },
  projects: [
    ...(adminOnly ? [] : [{ name: 'app-iphone13', testMatch: appOnly ? `**/app-${process.env.QA_SCOPE}.spec.mjs` : '**/app.spec.mjs', use: { ...devices['iPhone 13'], browserName: 'chromium', baseURL: APP_URL } }]),
    ...(appOnly ? [] : [{ name: 'admin-desktop', testMatch: adminOnly ? '**/admin-m3.spec.mjs' : '**/admin.spec.mjs', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 }, baseURL: ADMIN_URL } }]),
  ],
});
