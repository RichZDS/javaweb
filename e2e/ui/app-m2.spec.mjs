import { test, expect } from '@playwright/test';
import { m2Fixture, paperTitles } from '../lib/m2-fixtures.mjs';
import { step, tap, button, responseFor } from './support.mjs';

test('QA-RUN-02 考试：断网拦截→恢复补传→关页重开→手动/到时交卷→统计对账', async ({ page: firstPage, context }, info) => {
  test.setTimeout(210000);
  const f = await m2Fixture();
  let page = firstPage;
  let sheet;
  let minute;
  const evidence = { main: process.env.QA_MAIN_COMMIT || 'db63cdc', userId: f.user.id, submitRequests: [], pageErrors: [], stats: [] };
  const appUrl = process.env.APP_URL || 'http://127.0.0.1:4173/quiz/';
  context.on('page', p => p.on('pageerror', error => evidence.pageErrors.push(error.message)));
  page.on('pageerror', error => evidence.pageErrors.push(error.message));
  context.on('request', request => {
    const path = new URL(request.url()).pathname;
    if (/\/api\/exam-records\/\d+\/submit$/.test(path) && request.method() === 'POST') evidence.submitRequests.push(path);
  });
  await context.route('**/*', route => ['127.0.0.1', 'localhost', '[::1]'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
  const qwrap = index => page.locator('.qwrap').filter({ has: page.getByText(`第 ${index + 1} 题`, { exact: true }) });
  const selectKey = async (index, key, judge = false) => qwrap(index).locator('.qb-option').filter({ has: page.locator('.qb-key').filter({ hasText: new RegExp(`^${judge ? key === 'T' ? '对' : '错' : key}$`) }) }).click();
  const modal = () => page.locator('.uni-modal:visible');
  const confirm = () => modal().locator('.uni-modal__btn_primary').click();
  const cancel = () => modal().locator('.uni-modal__btn_default').click();
  const saved = async (recordId, questionId) => (await f.api.get(`/api/exam-records/${recordId}`)).answers.find(a => a.questionId === questionId);

  await step(page, info, '01_AC-04_登录与考试列表', async () => {
    await page.goto(appUrl, { waitUntil: 'domcontentloaded' });
    const field = label => page.locator('.field').filter({ has: page.locator('.label').filter({ hasText: new RegExp(`^${label}$`) }) }).locator('input');
    await field('用户名').fill(f.credentials.username); await field('密码').fill(f.credentials.password);
    await responseFor(page, '/api/auth/login', () => button(page, '登录').click());
    await expect(page.locator('.hero-exam')).toHaveText(f.root.name);
    await tap(page, '考试');
    await expect(page.getByText(paperTitles.fixed, { exact: true })).toBeVisible();
  });
  await step(page, info, '02_EX-01_须知并开考', async () => {
    const card = page.locator('.exams .paper').filter({ has: page.getByText(paperTitles.fixed, { exact: true }) });
    await button(card, '考前须知').click(); await expect(page.getByText('考试须知', { exact: true })).toBeVisible();
    sheet = await responseFor(page, `/api/exams/${f.paper.paperId}/start`, () => button(page, '开始考试').click());
    expect(sheet.questions.length).toBe(4); evidence.recordId = sheet.recordId; evidence.deadlineAt = sheet.deadlineAt;
    await expect(page.locator('.clock')).toBeVisible();
  });
  await step(page, info, '03_EX-23_首题保存并标记', async () => {
    const path = `/api/exam-records/${sheet.recordId}/answers/${sheet.questions[0].questionId}`;
    expect((await responseFor(page, path, () => selectKey(0, 'A'), 'PUT')).accepted).toBe(true);
    await responseFor(page, path, () => tap(page.locator('.bottombar'), '稍后再看'), 'PUT');
    await expect(page.locator('.sync')).toHaveText('答案已保存');
    const current = await saved(sheet.recordId, sheet.questions[0].questionId);
    expect(current.userAnswer).toEqual(['A']); expect(current.marked).toBe(true);
    evidence.firstSavedVersion = current.version;
  });
  await step(page, info, '04_EX-08_断网作答保留未同步', async () => {
    await context.setOffline(true);
    await tap(page.locator('.bottombar'), '下一题');
    await selectKey(1, 'A'); await selectKey(1, 'C');
    await expect(page.locator('.sync')).toContainText('题未同步');
    expect((await saved(sheet.recordId, sheet.questions[1].questionId))?.version || 0).toBe(0);
  });
  await step(page, info, '05_EX-08_未同步交卷被拦截', async () => {
    const before = evidence.submitRequests.length;
    await page.locator('.top-btn.strong').click();
    await expect(modal()).toContainText('还有 2 题没答'); await confirm();
    await expect(modal()).toContainText('还不能交卷');
    expect(evidence.submitRequests.length).toBe(before);
    expect((await f.api.get(`/api/exam-records/${sheet.recordId}`)).status).toBe('IN_PROGRESS');
    evidence.offlineSubmitBlocked = true;
  });
  await step(page, info, '06_EX-08_恢复网络自动补传', async () => {
    await confirm(); await context.setOffline(false);
    await expect.poll(async () => (await saved(sheet.recordId, sheet.questions[1].questionId))?.userAnswer.slice().sort()).toEqual(['A', 'C']);
    await expect(page.locator('.sync')).toHaveText('答案已保存');
    evidence.reconnectedVersion = (await saved(sheet.recordId, sheet.questions[1].questionId)).version;
  });
  await step(page, info, '07_EX-12_离线第三题保存本机', async () => {
    await context.setOffline(true); await tap(page.locator('.bottombar'), '下一题'); await selectKey(2, 'T', true);
    await expect(page.locator('.sync')).toContainText('题未同步');
    expect((await saved(sheet.recordId, sheet.questions[2].questionId))?.version || 0).toBe(0);
  });
  await page.close(); await context.setOffline(false); page = await context.newPage();
  await step(page, info, '08_EX-12_关页重开恢复原场并补传', async () => {
    await page.goto(appUrl, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.hero-exam')).toHaveText(f.root.name); await tap(page, '考试');
    const card = page.locator('.exams .paper').filter({ has: page.getByText(paperTitles.fixed, { exact: true }) });
    await button(card, '继续考试').click();
    await expect(page).toHaveURL(new RegExp(`recordId=${sheet.recordId}`));
    await expect.poll(async () => (await saved(sheet.recordId, sheet.questions[2].questionId))?.userAnswer).toEqual(['T']);
    const stored = await f.api.get(`/api/exam-records/${sheet.recordId}`);
    expect(stored.deadlineAt).toBe(sheet.deadlineAt);
    expect(stored.answers.find(a => a.questionId === sheet.questions[0].questionId).marked).toBe(true);
    evidence.reopenPreservedRecord = true;
  });
  await step(page, info, '09_EX-05_未答提醒可取消', async () => {
    await page.locator('.top-btn.strong').click(); await expect(modal()).toContainText('还有 1 题没答');
    const before = evidence.submitRequests.length; await cancel();
    expect(evidence.submitRequests.length).toBe(before); await expect(page.locator('.clock')).toBeVisible();
  });
  await step(page, info, '10_EX-15_补全第四题并手动交卷', async () => {
    for (let i = 0; i < 3; i++) await tap(page.locator('.bottombar'), '下一题');
    await qwrap(3).locator('textarea').nth(0).fill('Integer'); await qwrap(3).locator('textarea').nth(1).fill('5');
    await expect.poll(async () => (await saved(sheet.recordId, sheet.questions[3].questionId))?.userAnswer).toEqual(['Integer', '5']);
    await page.locator('.top-btn.strong').click();
    const result = await responseFor(page, `/api/exam-records/${sheet.recordId}/submit`, confirm);
    expect(result.score).toBe(10); expect(result.correctCount).toBe(4); expect(result.unansweredCount).toBe(0);
    await expect(page.locator('.report:visible .summary .score')).toHaveText('10'); evidence.manualResult = result;
  });

  const verifyStats = async (answers, exams) => {
    await button(page, '返回考试列表').click(); await tap(page, '统计');
    const overview = await f.api.get('/api/stats/overview');
    expect(overview.answerCount).toBe(answers); expect(overview.examCount).toBe(exams); expect(overview.correctCount).toBe(answers);
    const values = { 累计作答: overview.answerCount, 正确率: `${Math.round(overview.correctRate * 100)}%`, 今日作答: overview.todayAnswerCount,
      学习天数: overview.studyDays, 连续打卡: `${overview.streakDays} 天`, 考试次数: overview.examCount };
    for (const [label, value] of Object.entries(values)) {
      const cell = page.locator('.stats .cell').filter({ has: page.getByText(label, { exact: true }) });
      await expect(cell.locator('.num')).toHaveText(String(value));
    }
    evidence.stats.push(overview);
  };
  await step(page, info, '11_ST-02_手动交卷后统计六项对账', () => verifyStats(4, 1));
  await step(page, info, '12_EX-06_一分钟卷开考并保存一题', async () => {
    await tap(page, '考试');
    const card = page.locator('.exams .paper').filter({ has: page.getByText(paperTitles.minute, { exact: true }) });
    await button(card, '考前须知').click();
    const p = (await f.api.get('/api/exams')).find(p => p.title === paperTitles.minute);
    minute = await responseFor(page, `/api/exams/${p.paperId}/start`, () => button(page, '开始考试').click());
    await responseFor(page, `/api/exam-records/${minute.recordId}/answers/${minute.questions[0].questionId}`, () => selectKey(0, 'A'), 'PUT');
    evidence.minuteRecordId = minute.recordId; evidence.minuteDeadlineAt = minute.deadlineAt;
  });
  await step(page, info, '13_EX-06_真实等待截止自动交卷', async () => {
    const before = evidence.submitRequests.length;
    await expect(page.locator('.report:visible .summary .score')).toHaveText('1', { timeout: 75000 });
    const result = await f.api.get(`/api/exam-records/${minute.recordId}`);
    expect(result.status).toBe('FINISHED'); expect(result.score).toBe(1); expect(result.unansweredCount).toBe(3);
    expect(result.submittedAt).toBe(minute.deadlineAt); expect(evidence.submitRequests.length).toBeGreaterThan(before);
    evidence.automaticResult = result;
  });
  await step(page, info, '14_ST-02_到时交卷后统计再次对账', () => verifyStats(5, 2));
  await step(page, info, '15_DY-08_排行榜本人数据核对', async () => {
    await tap(page, '本周排行榜'); const board = await f.api.get('/api/stats/leaderboard?period=WEEK');
    await expect(page.locator('.me-rank')).toHaveText(`第 ${board.me.rank} 名`);
    await expect(page.locator('.board .me')).toContainText(`本周 ${board.me.answerCount} 次`);
    expect(board.me.answerCount).toBe(5); await expect(page.locator('.board .list .row')).toHaveCount(board.items.length);
    evidence.leaderboardMe = board.me;
  });
  expect(evidence.pageErrors).toEqual([]);
  await info.attach('M2 用户走查结果（不含认证数据）', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
});
