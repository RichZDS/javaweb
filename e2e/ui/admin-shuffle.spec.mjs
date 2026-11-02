import { test, expect, devices } from '@playwright/test';
import { fixture, paper } from '../lib/fixtures.mjs';
import { APP_URL, ADMIN_URL, allowTestHost } from '../lib/stack-urls.mjs';
import { step, tap, button, responseFor } from './support.mjs';

// Manual screenshots mask the administrator identity; auth responses are never attached.
test.use({ screenshot: 'off' });
test('D2 AD-08 EX-10：后台开启打乱并上架，App按新标签作答，对错解析对应原选项', async ({ page, context, browser }, info) => {
  test.setTimeout(360000);
  const f = await fixture({ count: 12, types: ['SINGLE'] });
  const p = await paper(f, { shuffleOptions: false, questions: f.questions.map((q, i) => ({ questionId: q.id, score: 1, sort: i })) }, false);
  const evidence = { main: process.env.QA_MAIN_COMMIT, userId: f.user.id, paperId: p.id, pageErrors: [] };
  page.on('pageerror', e => evidence.pageErrors.push(e.message));
  await context.route('**/*', allowTestHost);
  await page.goto(ADMIN_URL, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('用户名', { exact: true }).fill(process.env.ADMIN_USERNAME || 'admin');
  await page.getByLabel('密码', { exact: true }).fill(process.env.ADMIN_PASSWORD || 'admin123');
  await responseFor(page, '/api/auth/login', () => page.getByRole('button', { name: '登录', exact: true }).click());
  // SPA navigation downloads lazy chunks after login; staging's cross-border link needs the navigation budget too.
  await page.getByRole('menuitem', { name: '试卷管理', exact: true }).waitFor({ state: 'visible', timeout: 90000 });
  await page.getByRole('menuitem', { name: '试卷管理', exact: true }).click();
  const row = () => page.getByRole('row').filter({ has: page.getByText(p.title, { exact: true }) });
  const mask = { mask: [page.locator('.who'), page.locator('input[type=password]')] };
  await step(page, info, '01_后台编辑自建卷开启选项打乱', async () => {
    await row().getByRole('button', { name: '编辑', exact: true }).click();
    await expect(page.locator('.el-form-item').filter({ has: page.getByText('标题', { exact: true }) }).locator('input')).toHaveValue(p.title, { timeout: 90000 });
    const field = page.locator('.el-form-item').filter({ has: page.getByText('选项打乱', { exact: true }) });
    const toggle = field.getByRole('switch');
    await expect(toggle).not.toBeChecked(); await field.locator('.el-switch').click(); await expect(toggle).toBeChecked();
    const saved = await responseFor(page, `/api/admin/papers/${p.id}`, () => page.getByRole('button', { name: '保存', exact: true }).click(), 'PUT');
    expect(saved.shuffleOptions).toBe(true); evidence.adminSavedShuffle = saved.shuffleOptions;
  }, mask);
  await step(page, info, '02_后台上架并显示选项随机', async () => {
    await page.getByRole('button', { name: '返回列表', exact: true }).click();
    const saved = await responseFor(page, `/api/admin/papers/${p.id}/status`, () => row().getByRole('button', { name: '上架', exact: true }).click(), 'PUT');
    expect(saved.status).toBe('ONLINE'); expect(saved.shuffleOptions).toBe(true);
    await expect(row()).toContainText('选项随机'); await expect(row()).toContainText('已上架');
  }, mask);
  // Sequential contexts in one browser: administrator UI first, then learner mobile UI.
  await context.close();
  const mobile = await browser.newContext({ ...devices['iPhone 13'] });
  await mobile.route('**/*', allowTestHost);
  const app = await mobile.newPage(); app.setDefaultTimeout(15000); app.setDefaultNavigationTimeout(90000);
  app.on('pageerror', e => evidence.pageErrors.push(e.message));
  const qwrap = index => app.locator('.qwrap').filter({ has: app.getByText(`第 ${index + 1} 题`, { exact: true }) });
  const choose = (scope, key) => scope.locator('.qb-option').filter({ has: app.locator('.qb-key').filter({ hasText: new RegExp(`^${key}$`) }) }).click();
  let sheet, wrongIndex, result;
  try {
    await step(app, info, '03_App登录并查看随机选项须知', async () => {
      await app.goto(APP_URL, { waitUntil: 'domcontentloaded' });
      const field = label => app.locator('.field').filter({ has: app.locator('.label').filter({ hasText: new RegExp(`^${label}$`) }) }).locator('input');
      await field('用户名').fill(f.credentials.username); await field('密码').fill(f.credentials.password);
      await responseFor(app, '/api/auth/login', () => button(app, '登录').click());
      await expect(app.locator('.hero-exam')).toHaveText(f.root.name, { timeout: 90000 }); await tap(app, '考试');
      const card = app.locator('.exams .paper').filter({ hasText: p.title });
      await expect(card).toContainText('选项随机', { timeout: 90000 }); await button(card, '考前须知').click();
      await expect(app.getByText(/本卷选项顺序随机/)).toBeVisible({ timeout: 90000 });
    });
    await step(app, info, '04_换位题使用原字母答错其余按新标签答对', async () => {
      sheet = await responseFor(app, `/api/exams/${p.id}/start`, () => button(app, '开始考试').click());
      expect(sheet.shuffleOptions).toBe(true); wrongIndex = sheet.questions.findIndex(q => q.optionMapping.A !== 'A');
      expect(wrongIndex, '至少一题原A换位才构成差分证明').toBeGreaterThanOrEqual(0);
      evidence.recordId = sheet.recordId; evidence.wrongIndex = wrongIndex + 1;
      evidence.mapping = sheet.questions[wrongIndex].optionMapping;
      for (const [index, q] of sheet.questions.entries()) {
        for (const option of q.options) {
          const original = f.questions.find(x => x.id === q.questionId).options.find(x => x.key === q.optionMapping[option.key]);
          expect(option.text).toBe(original.text);
          const visible = qwrap(index).locator('.qb-option').filter({ has: app.locator('.qb-key').filter({ hasText: new RegExp(`^${option.key}$`) }) });
          await expect(visible).toContainText(original.text);
        }
        const displayed = index === wrongIndex ? 'A' : Object.entries(q.optionMapping).find(([, original]) => original === 'A')[0];
        const saved = await responseFor(app, `/api/exam-records/${sheet.recordId}/answers/${q.questionId}`, () => choose(qwrap(index), displayed), 'PUT');
        expect(saved.accepted).toBe(true);
        if (index < sheet.questions.length - 1) await tap(app.locator('.bottombar'), '下一题');
      }
      await expect(app.locator('.sync')).toHaveText('答案已保存');
    });
    await step(app, info, '05_交卷得到11对1错且成绩持久化一致', async () => {
      await app.locator('.top-btn.strong').click();
      result = await responseFor(app, `/api/exam-records/${sheet.recordId}/submit`, () => app.locator('.uni-modal:visible .uni-modal__btn_primary').click());
      expect(result.score).toBe(11); expect(result.correctCount).toBe(11); expect(result.wrongCount).toBe(1);
      await expect(app.locator('.report .summary .score')).toHaveText('11');
      expect(await f.api.get(`/api/exam-records/${sheet.recordId}`)).toEqual(result);
      evidence.result = { score: result.score, correctCount: result.correctCount, wrongCount: result.wrongCount };
    });
    await step(app, info, '06_错题解析展示新答案并标注原选项', async () => {
      await tap(app.locator('.filters'), '答错');
      const item = result.items.find(x => x.questionId === sheet.questions[wrongIndex].questionId);
      expect(item.correct).toBe(false); expect(item.userAnswer).toEqual(['A']);
      const shownCorrect = Object.entries(item.optionMapping).find(([, original]) => original === 'A')[0];
      expect(item.correctAnswer).toEqual([shownCorrect]); expect(shownCorrect).not.toBe('A');
      const row = app.locator('.report .item').filter({ has: app.getByText(`第 ${item.index} 题`, { exact: true }) });
      await row.locator('.item-head').click();
      await expect(row.locator('.ar-verdict')).toContainText('回答错误');
      await expect(row.locator('.ar-orig')).toHaveText('（对应解析里的 A）');
      await expect(row.locator('.ar-shuffle-tip')).toContainText('解析里的字母是原选项');
      await expect(row.locator('.qb-option.is-correct .qb-key')).toHaveText(shownCorrect);
      await expect(row.locator('.qb-option.is-wrong .qb-key')).toHaveText('A');
      for (const [display, original] of Object.entries(item.optionMapping)) if (display !== original) {
        const option = row.locator('.qb-option').filter({ has: app.locator('.qb-key').filter({ hasText: new RegExp(`^${display}$`) }) });
        await expect(option.locator('.qb-orig')).toHaveText(`原选项 ${original}`);
      }
      const wrong = (await f.api.get('/api/wrong-questions')).items.find(x => x.questionId === item.questionId);
      expect(wrong.lastWrongAnswer).toEqual([item.optionMapping.A]); evidence.wrongItem = item; evidence.lastWrongAnswer = wrong.lastWrongAnswer;
    });
    expect(evidence.pageErrors).toEqual([]);
    await info.attach('后台至App打乱实证（无认证数据）', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
  } finally { await mobile.close(); }
});
