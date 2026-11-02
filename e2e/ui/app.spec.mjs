import { test, expect } from '@playwright/test';
import { fixture, paper } from '../lib/fixtures.mjs';
import { unique, password, Api } from '../lib/http.mjs';
import { step, tap, button, responseFor } from './support.mjs';

test('P0 学员主流程：注册→目标→10题→错题重练→固定考试→统计', async ({ page }, info) => {
  const f = await fixture({ count: 12, types: ['SINGLE'] });
  const p = await paper(f);
  const credentials = { username: unique('ui'), password: password() };
  let api;
  let session;
  await step(page, info, '01_AC-01_注册', async () => {
    await page.goto('/');
    await tap(page, '注册');
    await page.getByPlaceholder(/用户名/).fill(credentials.username);
    await page.getByPlaceholder(/^密码|设置密码/).fill(credentials.password);
    const confirm = page.getByPlaceholder(/确认密码|再次输入密码/);
    if (await confirm.count()) await confirm.fill(credentials.password);
    const auth = await responseFor(page, '/api/auth/register', () => button(page, '注册').click());
    api = new Api(auth.token);
    expect(auth.user.username).toBe(credentials.username);
  });
  await step(page, info, '02_AC-08_选择目标考试', async () => {
    await tap(page, f.root.name);
    await button(page, '确定|确认选择|开始学习').click();
    await expect.poll(async () => (await api.get('/api/me')).targetCategoryId).toBe(f.root.id);
  });
  await step(page, info, '03_PR-01_开始随机十题', async () => {
    await tap(page, '随机刷题');
    await tap(page, '10');
    session = await responseFor(page, '/api/practice/sessions', () => button(page, '开始练习|开始刷题').click());
    expect(session.questionCount).toBe(10);
    expect(new Set(session.questions.map(q => q.id)).size).toBe(10);
  });
  for (let i = 0; i < 10; i++) {
    await step(page, info, `04_PR-06_第${i + 1}题`, async () => {
      await tap(page, i === 0 ? '乙' : '甲');
      const submitted = await responseFor(page, `/api/practice/sessions/${session.id}/answers`, () => button(page, '提交答案').click());
      expect(submitted.correct).toBe(i !== 0);
      if (i < 9) await button(page, '下一题').click();
      else if (await button(page, '查看结果|完成练习').isVisible()) await button(page, '查看结果|完成练习').click();
    });
  }
  await step(page, info, '05_PR-12_练习结果', async () => {
    await expect(page.getByText(/正确率/)).toBeVisible();
    const stored = await api.get(`/api/practice/sessions/${session.id}`);
    expect(stored.answeredCount).toBe(10);
    expect(stored.correctCount).toBe(9);
  });
  await step(page, info, '06_WR-01_WR-03_错题本重练', async () => {
    await button(page, '返回首页|回到首页').click();
    await tap(page, '错题本');
    await expect(page.getByText(/错误次数|错了/)).toBeVisible();
    const retry = await responseFor(page, '/api/practice/sessions', () => button(page, '重练错题|开始重练|重练全部').click());
    expect(retry.questionCount).toBe(1);
    await tap(page, '甲');
    const result = await responseFor(page, `/api/practice/sessions/${retry.id}/answers`, () => button(page, '提交答案').click());
    expect(result.correct).toBe(true);
    if (await button(page, '查看结果|完成练习').isVisible()) await button(page, '查看结果|完成练习').click();
    await button(page, '返回首页|回到首页').click();
  });
  await step(page, info, '07_EX-01_EX-02_固定卷开考', async () => {
    await tap(page, '考试');
    await tap(page, p.title);
    await expect(page.getByText(/考试须知|考前须知/)).toBeVisible();
  });
  let sheet;
  await step(page, info, '08_EX-03_考试作答', async () => {
    sheet = await responseFor(page, `/api/exams/${p.id}/start`, () => button(page, '开始考试').click());
    for (let i = 0; i < sheet.questions.length; i++) {
      await tap(page, '甲');
      if (i < sheet.questions.length - 1) await button(page, '下一题').click();
    }
  });
  await step(page, info, '09_EX-15_成绩单', async () => {
    await button(page, '交卷').click();
    await button(page, '确定|确认交卷').click();
    await expect(page.getByText(/成绩单|考试结果/)).toBeVisible();
    const result = await api.get(`/api/exam-records/${sheet.recordId}`);
    expect(result.status).toBe('FINISHED');
    expect(result.score).toBe(result.totalScore);
  });
  await step(page, info, '10_ST-02_统计', async () => {
    await button(page, '返回首页|回到首页').click();
    await tap(page, '统计');
    await expect(page.getByText(/作答次数/)).toBeVisible();
    const stats = await api.get('/api/stats/overview');
    expect(stats.answerCount).toBe(15);
    expect(stats.correctCount).toBe(14);
  });
});
