import { test, expect } from '@playwright/test';
import { Api, password } from '../lib/http.mjs';
import { fixture, practice, answer, correct, incorrect, paper } from '../lib/fixtures.mjs';
import { APP_URL } from '../lib/stack-urls.mjs';
import { step, tap, button, responseFor } from './support.mjs';

test('QA-INFO-02 设置与PM-M3-04/05：未答展示、统计隔离、字号夜间、阈值密码更新', async ({ page, context }, info) => {
  test.setTimeout(180000);
  const a = await fixture({ count: 6 }); const b = await fixture({ count: 1 });
  a.chapter = await a.staff.put(`/api/admin/categories/${a.chapter.id}`, { name: `${a.tag}A弱项章`, sort: 0 });
  b.chapter = await b.staff.put(`/api/admin/categories/${b.chapter.id}`, { name: `${b.tag}B新章`, sort: 0 });
  const p = await practice(a, { acceptFewer: true });
  for (const [i, q] of p.questions.slice(0, 5).entries()) await answer(a, p, q, i === 0 ? correct(q) : incorrect(q));
  await a.api.post(`/api/practice/sessions/${p.id}/finish`, {});
  const exam = await paper(a); const sheet = await a.api.post(`/api/exams/${exam.id}/start`, {}, 201);
  await a.api.put(`/api/exam-records/${sheet.recordId}/answers/${sheet.questions[0].questionId}`, { userAnswer: ['A'], version: 1, marked: false });
  const result = await a.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  const evidence = { main: process.env.QA_MAIN_COMMIT || 'a05d377', userId: a.user.id, roots: [a.root.id, b.root.id], examRecordId: sheet.recordId, pageErrors: [] };
  page.on('pageerror', error => evidence.pageErrors.push(error.message));
  await context.route('**/*', route => ['localhost', '127.0.0.1', '[::1]'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
  await step(page, info, '01_登录与考试记录', async () => {
    await page.goto(APP_URL, { waitUntil: 'domcontentloaded' });
    const field = label => page.locator('.field').filter({ has: page.locator('.label').filter({ hasText: new RegExp(`^${label}$`) }) }).locator('input');
    await field('用户名').fill(a.credentials.username); await field('密码').fill(a.credentials.password);
    await responseFor(page, '/api/auth/login', () => button(page, '登录').click());
    await expect(page.locator('.hero-exam')).toHaveText(a.root.name); await tap(page, '我的'); await tap(page, '考试记录');
    await page.locator('.rec').filter({ hasText: exam.title }).click();
    await expect(page.locator('.report .summary .score')).toHaveText('3');
  });
  await step(page, info, '02_PM-M3-04_未答填空展开显示未作答', async () => {
    await page.locator('.filters').getByText('未答', { exact: true }).click();
    const fill = result.items.find(q => q.type === 'FILL');
    const card = page.locator('.report .item').filter({ has: page.getByText(`第 ${fill.index} 题`, { exact: true }) });
    await card.locator('.item-head').click();
    await expect(card.locator('.ar-verdict')).toHaveText('— 未作答');
    await expect(card.getByText('✗ 回答错误', { exact: true })).toHaveCount(0);
    expect(result.wrongCount).toBe(0); expect(result.unansweredCount).toBe(3); evidence.unansweredShownSeparately = true;
  });
  await step(page, info, '03_PM-M3-05_A考试已有薄弱点', async () => {
    await button(page, '返回考试列表').click(); await tap(page, '统计');
    const weak = await a.api.get('/api/stats/weak?days=30&limit=5'); expect(weak.length).toBe(1);
    await expect(page.locator('.stats .weak-name')).toHaveText(a.chapter.name); evidence.weakA = weak;
  });
  let failWeak = true;
  const weakRoute = route => failWeak ? route.abort('failed') : route.continue();
  await page.route('**/api/stats/weak?*', weakRoute);
  await step(page, info, '04_PM-M3-05_切B且薄弱点请求失败不残留A', async () => {
    await tap(page, '首页'); await page.locator('.hero-switch').click();
    await responseFor(page, '/api/me', () => tap(page, b.root.name), 'PUT');
    await expect(page.locator('.hero-exam')).toHaveText(b.root.name); await tap(page, '统计');
    await expect(page.locator('.stats .cell').filter({ has: page.getByText('累计作答', { exact: true }) }).locator('.num')).toHaveText('0');
    await expect(page.getByText(`${b.root.name}的薄弱点没加载出来`, { exact: true })).toBeVisible();
    await expect(page.locator('.stats .weak')).toHaveCount(0);
    await expect(page.locator('.stats').getByText(a.chapter.name, { exact: true })).toHaveCount(0); evidence.weakScopeIsolated = true;
  });
  await step(page, info, '05_PM-M3-05_恢复后单模块重试', async () => {
    failWeak = false;
    const error = page.locator('.module-error').filter({ hasText: `${b.root.name}的薄弱点没加载出来` });
    await button(error, '重试').click(); await expect(error).toHaveCount(0);
    await expect(page.getByText('暂时没有：多做几道题再来看', { exact: true })).toBeVisible();
    expect(await a.api.get('/api/stats/weak?days=30&limit=5')).toEqual([]); evidence.weakRetryRecovered = true;
  });
  await page.unroute('**/api/stats/weak?*', weakRoute);
  await step(page, info, '06_设置_字号与夜间模式', async () => {
    await tap(page, '我的'); await tap(page, '设置');
    const before = await page.locator('.preview-text').evaluate(el => Number.parseFloat(getComputedStyle(el).fontSize));
    await page.locator('.seg').getByText('大', { exact: true }).click();
    const after = await page.locator('.preview-text').evaluate(el => Number.parseFloat(getComputedStyle(el).fontSize));
    expect(after).toBeGreaterThan(before);
    const night = page.locator('.settings .row').filter({ has: page.getByText('夜间模式', { exact: true }) });
    await night.locator('uni-switch').click(); await expect(page.locator('html')).toHaveClass(/quiz-dark/);
    evidence.fontSizes = { before, after }; evidence.nightMode = true;
  });
  await step(page, info, '07_设置_错题阈值保存并重开保持', async () => {
    const updated = await responseFor(page, '/api/me/settings', () => page.locator('.stepper').getByText('＋', { exact: true }).click(), 'PUT');
    expect(updated.wrongRemoveThreshold).toBe(4); await expect(page.locator('.step-val')).toHaveText('4 次');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.locator('.step-val')).toHaveText('4 次'); await expect(page.locator('.seg-item.on')).toHaveText('大');
    await expect(page.locator('html')).toHaveClass(/quiz-dark/);
    expect((await a.api.get('/api/me')).wrongRemoveThreshold).toBe(4); evidence.settingsPersisted = true;
  });
  await step(page, info, '08_设置_H5更新与提醒说明', async () => {
    await expect(page.getByText('网页版不支持提醒，请在手机 App 里打开', { exact: true })).toBeVisible();
    await tap(page, '检查更新');
    await expect(page.getByText('网页版总是最新，不需要更新', { exact: true })).toBeVisible();
    evidence.h5UpdateHint = true; evidence.nativeReminderUntested = true;
  });
  const nextPassword = password();
  await step(page, info, '09_设置_原密码错误被拒', async () => {
    await tap(page, '修改密码'); const inputs = page.locator('.uni-popup .dialog input');
    await inputs.nth(0).fill(password()); await inputs.nth(1).fill(nextPassword); await inputs.nth(2).fill(nextPassword);
    const pending = page.waitForResponse(r => new URL(r.url()).pathname === '/api/me/password' && r.request().method() === 'PUT');
    await button(page.locator('.uni-popup .dialog'), '保存').click(); const response = await pending;
    expect(response.status()).toBe(400); await expect(page.locator('.dialog-err')).toBeVisible();
  });
  await step(page, info, '10_设置_改密后新密码可用旧密码失效', async () => {
    await page.locator('.uni-popup .dialog input').nth(0).fill(a.credentials.password);
    await responseFor(page, '/api/me/password', () => button(page.locator('.uni-popup .dialog'), '保存').click(), 'PUT');
    await expect(page.locator('.uni-popup:visible')).toHaveCount(0);
    await new Api().post('/api/auth/login', a.credentials, 401, 'UNAUTHORIZED');
    const auth = await new Api().post('/api/auth/login', { username: a.credentials.username, password: nextPassword });
    expect(auth.user.id).toBe(a.user.id); evidence.passwordChanged = true;
  });
  expect(evidence.pageErrors).toEqual([]);
  await info.attach('设置与PM-M3-04/05复测（无认证数据）', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
});
