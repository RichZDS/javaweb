import { defineConfig, devices } from '@playwright/test';
import { APP_URL, ADMIN_URL } from './lib/stack-urls.mjs';

process.env.PLAYWRIGHT_BROWSERS_PATH ||= '/data/ms-playwright';
const appOnly = ['m1', 'm2', 'pm', 'settings', 'shuffle'].includes(process.env.QA_SCOPE);
const adminOnly = ['m3', 'admin-shuffle'].includes(process.env.QA_SCOPE);
export default defineConfig({
  testDir: './ui', testMatch: '**/*.spec.mjs',
  fullyParallel: false, workers: 1, retries: 0, timeout: 180000,
  expect: { timeout: 10000 },
  outputDir: './artifacts/ui',
  reporter: [['list'], ['json', { outputFile: './artifacts/ui-results.json' }]],
  use: { screenshot: 'only-on-failure', trace: 'off', video: 'off', actionTimeout: 15000, navigationTimeout: process.env.QA_STAGING === '1' ? 90000 : 20000,
    launchOptions: process.env.QA_STAGING === '1' ? { args: ['--no-proxy-server'] } : {} },
  projects: [
    ...(adminOnly ? [] : [{ name: 'app-iphone13', testMatch: appOnly ? `**/app-${process.env.QA_SCOPE}.spec.mjs` : '**/app.spec.mjs', use: { ...devices['iPhone 13'], browserName: 'chromium', baseURL: APP_URL } }]),
    ...(appOnly ? [] : [{ name: 'admin-desktop', testMatch: adminOnly ? `**/admin-${process.env.QA_SCOPE === 'm3' ? 'm3' : 'shuffle'}.spec.mjs` : '**/admin.spec.mjs', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 }, baseURL: ADMIN_URL } }]),
  ],
});
