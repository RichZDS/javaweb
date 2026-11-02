import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

const stage = (process.env.QA_STAGE || 'm1').replace(/[^a-zA-Z0-9_-]/g, '_');
const run = new Date().toISOString().replace(/[:.]/g, '-');
export const shotRoot = process.env.SHOT_DIR || `/data/crew/qzd-5b1e/shots/qa-${stage}`;

export async function step(page, info, name, action, screenshotOptions = {}) {
  return test.step(name, async () => {
    const dir = path.join(shotRoot, run, info.project.name);
    await mkdir(dir, { recursive: true });
    let actionFailed = false;
    try {
      await action();
      // Capture the settled screen after uni-app's transient toast/loading fades.
      await expect(page.locator('.uni-toast:visible, .uni-sample-toast:visible, .el-message:visible')).toHaveCount(0, { timeout: 7000 });
    }
    catch (error) { actionFailed = true; throw error; }
    finally {
      const file = path.join(dir, `${name.replace(/[^a-zA-Z0-9\u4e00-\u9fff_-]/g, '_')}.png`);
      try {
        await page.screenshot({ path: file, fullPage: true, animations: 'disabled', ...screenshotOptions });
        await info.attach(name, { path: file, contentType: 'image/png' });
      } catch (error) { if (!actionFailed) throw error; }
    }
  });
}

// UI controls are isolated here so labels can be calibrated after the first frontend merge.
// A missing control fails the scenario; no API shortcut silently replaces a UI action.
export const button = (page, name) => page.locator('button, uni-button').filter({ hasText: new RegExp(`^\\s*(?:${name})\\s*$`) });
export async function tap(page, text) { await page.getByText(text, { exact: true }).click(); }
export async function responseFor(page, suffix, action, method = 'POST') {
  const pending = page.waitForResponse(r => new URL(r.url()).pathname.endsWith(suffix) && r.request().method() === method);
  await action();
  const response = await pending;
  expect(response.ok(), `${method} ${suffix} 返回 ${response.status()}`).toBeTruthy();
  const body = await response.json();
  expect(body.code).toBe('OK');
  return body.data;
}
export async function selectCategory(page, label, f, leaf = true) {
  await page.getByLabel(label, { exact: true }).click();
  await tap(page, f.root.name);
  if (leaf) { await tap(page, f.subject.name); await tap(page, f.chapter.name); }
}
