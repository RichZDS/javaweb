import { test, expect } from '@playwright/test';
import { fixture, paper } from '../lib/fixtures.mjs';
import { APP_URL, allowTestHost } from '../lib/stack-urls.mjs';
import { step, tap, button, responseFor } from './support.mjs';

test('D2 页面：练习/考试选项打乱，新标签作答，原选项标注明确', async ({ page, context }, info) => {
  test.setTimeout(180000);
  const f = await fixture({ count: 12, types: ['SINGLE'] });
  const p = await paper(f, { shuffleOptions: true, questions: f.questions.map((q, i) => ({ questionId: q.id, score: 1, sort: i })) });
  const evidence = { main: process.env.QA_MAIN_COMMIT || 'a0dae28', userId: f.user.id, pageErrors: [] };
  page.on('pageerror', error => evidence.pageErrors.push(error.message));
  await context.route('**/*', allowTestHost);
  const qwrap = index => page.locator('.qwrap').filter({ has: page.getByText(`第 ${index + 1} 题`, { exact: true }) });
  const choose = (scope, key) => scope.locator('.qb-option').filter({ has: page.locator('.qb-key').filter({ hasText: new RegExp(`^${key}$`) }) }).click();
  const shownA = q => Object.entries(q.optionMapping).find(([, original]) => original === 'A')[0];
  let session, index;
  await step(page, info, '01_登录与目标考试', async () => {
    await page.goto(APP_URL, { waitUntil: 'domcontentloaded' });
    const field = label => page.locator('.field').filter({ has: page.locator('.label').filter({ hasText: new RegExp(`^${label}$`) }) }).locator('input');
    await field('用户名').fill(f.credentials.username); await field('密码').fill(f.credentials.password);
    await responseFor(page, '/api/auth/login', () => button(page, '登录').click());
    await expect(page.locator('.hero-exam')).toHaveText(f.root.name);
  });
  await step(page, info, '02_开启选项打乱并取得换位样本', async () => {
    for (let attempt = 0; attempt < 3; attempt++) {
      await tap(page, '随机刷题'); await page.locator('.subject-name').filter({ hasText: f.subject.name }).click();
      await page.locator('.recite').filter({ has: page.getByText('选项顺序打乱', { exact: true }) }).locator('uni-switch').click();
      session = await responseFor(page, '/api/practice/sessions', () => button(page, '开始刷题').click());
      expect(session.shuffleOptions).toBe(true); index = session.questions.findIndex(q => q.optionMapping.A !== 'A');
      evidence.sampleAttempts = attempt + 1;
      if (index >= 0) break;
      await page.locator('.top-btn.strong').click(); await page.locator('.uni-modal:visible .uni-modal__btn_primary').click();
      await button(page, '返回首页').click();
    }
    expect(index, '需要原A正确字母换位样本，不能把同位置样本当差分证明').toBeGreaterThanOrEqual(0);
    for (let i = 0; i < index; i++) await tap(page.locator('.bottombar'), '下一题');
    await expect(qwrap(index)).toBeVisible(); evidence.practiceMapping = session.questions[index].optionMapping;
  });
  await step(page, info, '03_按新标签答对并显示原选项', async () => {
    const q = session.questions[index]; const shown = shownA(q); expect(shown).not.toBe('A');
    await choose(qwrap(index), shown);
    const result = await responseFor(page, `/api/practice/sessions/${session.id}/answers`, () => button(qwrap(index), '提交答案').click());
    expect(result.correct).toBe(true); expect(result.correctAnswer).toEqual([shown]);
    await expect(qwrap(index).locator('.ar-verdict')).toHaveText('✓ 回答正确');
    await expect(qwrap(index).locator('.ar-orig')).toHaveText('（对应解析里的 A）');
    for (const [display, original] of Object.entries(q.optionMapping)) if (display !== original) {
      const option = qwrap(index).locator('.qb-option').filter({ has: page.locator('.qb-key').filter({ hasText: new RegExp(`^${display}$`) }) });
      await expect(option.locator('.qb-orig')).toHaveText(`原选项 ${original}`);
    }
    await expect(qwrap(index).locator('.ar-shuffle-tip')).toContainText('解析里的字母是原选项');
    evidence.practiceSessionId = session.id; evidence.practiceDisplayedAnswer = shown;
  });
  await step(page, info, '04_结束练习并查看打乱卷须知', async () => {
    await page.locator('.top-btn.strong').click(); await page.locator('.uni-modal:visible .uni-modal__btn_primary').click();
    await button(page, '返回首页').click(); await tap(page, '考试');
    const card = page.locator('.exams .paper').filter({ hasText: p.title });
    await expect(card).toContainText('选项随机'); await button(card, '考前须知').click();
    await expect(page.getByText(/本卷选项顺序随机/)).toBeVisible();
  });
  let sheet;
  await step(page, info, '05_考试按展示顺序作答并保存', async () => {
    sheet = await responseFor(page, `/api/exams/${p.id}/start`, () => button(page, '开始考试').click());
    expect(sheet.shuffleOptions).toBe(true);
    for (const [i, q] of sheet.questions.entries()) {
      const shown = shownA(q);
      const saved = await responseFor(page, `/api/exam-records/${sheet.recordId}/answers/${q.questionId}`, () => choose(qwrap(i), shown), 'PUT');
      expect(saved.accepted).toBe(true);
      if (i < sheet.questions.length - 1) await tap(page.locator('.bottombar'), '下一题');
    }
    await expect(page.locator('.sync')).toHaveText('答案已保存'); evidence.examRecordId = sheet.recordId;
  });
  await step(page, info, '06_交卷判分和原选项成绩单', async () => {
    await page.locator('.top-btn.strong').click();
    const result = await responseFor(page, `/api/exam-records/${sheet.recordId}/submit`, () => page.locator('.uni-modal:visible .uni-modal__btn_primary').click());
    expect(result.score).toBe(12); expect(result.correctCount).toBe(12);
    await expect(page.locator('.report .summary .score')).toHaveText('12');
    await page.locator('.filters').getByText('全部', { exact: true }).click();
    const item = result.items.find(q => q.optionMapping.A !== 'A'); expect(item).toBeTruthy();
    const row = page.locator('.report .item').filter({ has: page.getByText(`第 ${item.index} 题`, { exact: true }) });
    await row.locator('.item-head').click();
    await expect(row.locator('.ar-orig')).toHaveText('（对应解析里的 A）');
    await expect(row.locator('.qb-orig').first()).toContainText('原选项');
    evidence.examResult = { score: result.score, correctCount: result.correctCount, item };
  });
  expect(evidence.pageErrors).toEqual([]);
  await info.attach('打乱页面实证（无认证数据）', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
});
