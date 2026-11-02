import { test, expect } from '@playwright/test';
import { Api, unique, password } from '../lib/http.mjs';
import { APP_URL } from '../lib/stack-urls.mjs';
import { step, tap, button, responseFor } from './support.mjs';

test('QA-RUN-01 App M1 真实页面：注册→目标→十题→结果→错题本→重练', async ({ page }, info) => {
  const credentials = { username: unique('qa_walk'), password: password() };
  const evidence = { main: process.env.QA_MAIN_COMMIT || 'af09a2d', steps: [], pageErrors: [], deferredRequests: [] };
  page.on('pageerror', error => evidence.pageErrors.push(error.message));
  page.on('response', response => {
    if (new URL(response.url()).pathname === '/api/stats/overview' && !response.ok()) {
      evidence.deferredRequests.push({ path: '/api/stats/overview', status: response.status() });
    }
  });
  let api;
  let session;
  let originalWrong;
  const target = 'Java 程序设计';
  const field = label => page.locator('.field').filter({ has: page.locator('.label').filter({ hasText: new RegExp(`^${label}$`) }) }).locator('input');
  await page.route('**/*', route => {
    const url = new URL(route.request().url());
    return ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname) ? route.continue() : route.abort();
  });
  await step(page, info, '01_AC-01_注册并登录', async () => {
    await page.goto(APP_URL, { waitUntil: 'domcontentloaded' });
    await tap(page, '去注册');
    await field('用户名').fill(credentials.username);
    await field('密码').fill(credentials.password);
    await field('确认密码').fill(credentials.password);
    const auth = await responseFor(page, '/api/auth/register', () => button(page, '注册并登录').click());
    api = new Api(auth.token);
    evidence.userId = auth.user.id;
    await expect(page.getByText('先选一个目标考试', { exact: true })).toBeVisible();
  });
  await step(page, info, '02_AC-08_选择Java考试', async () => {
    const user = await responseFor(page, '/api/me', () => tap(page, target), 'PUT');
    expect(user.targetCategoryName).toBe(target);
    await expect(page.locator('.hero-exam')).toHaveText(target);
    await expect(page.getByText('随机刷题', { exact: true })).toBeVisible();
    evidence.targetCategoryId = user.targetCategoryId;
  });
  await step(page, info, '03_PR-01_选章节并随机十题', async () => {
    await tap(page, '随机刷题');
    await page.locator('.subject-name').filter({ hasText: '基础语法' }).click();
    await tap(page, '10 题');
    session = await responseFor(page, '/api/practice/sessions', () => button(page, '开始刷题').click());
    expect(session.questionCount).toBe(10);
    expect(new Set(session.questions.map(q => q.id)).size).toBe(10);
    evidence.sessionId = session.id;
    evidence.questionIds = session.questions.map(q => q.id);
    await expect(page.locator('.progress-text')).toHaveText('1 / 10');
  });
  for (let i = 0; i < session.questions.length; i++) {
    await step(page, info, `04_PR-06_第${i + 1}题_提交与解析`, async () => {
      const question = session.questions[i];
      const detail = await api.get(`/api/questions/${question.id}`);
      const q = page.locator('.qwrap').filter({ has: page.getByText(`第 ${i + 1} 题`, { exact: true }) });
      await expect(q).toBeVisible();
      let answers = detail.type === 'FILL' ? detail.acceptedAnswers.map(a => a[0]) : detail.correctAnswer;
      if (i === 0) {
        originalWrong = question.id;
        answers = detail.type === 'FILL' ? answers.map(() => '故意错答') : detail.type === 'MULTIPLE' ? [answers[0]] : [detail.options.find(o => !answers.includes(o.key)).key];
      }
      if (detail.type === 'FILL') {
        for (let j = 0; j < answers.length; j++) await q.locator('textarea').nth(j).fill(answers[j]);
      } else {
        for (const key of answers) await q.locator('.qb-option').filter({ has: page.locator('.qb-key').filter({ hasText: new RegExp(`^${detail.type === 'JUDGE' ? key === 'T' ? '对' : '错' : key}$`) }) }).click();
      }
      const submitted = await responseFor(page, `/api/practice/sessions/${session.id}/answers`, () => button(q, '提交答案').click());
      expect(submitted.correct).toBe(i !== 0);
      await expect(q.locator('.ar-verdict')).toHaveText(i === 0 ? '✗ 回答错误' : '✓ 回答正确');
      evidence.steps.push({ questionId: question.id, type: detail.type, correct: submitted.correct });
    });
    if (i < 9) {
      await tap(page.locator('.bottombar'), '下一题');
      await expect(page.locator('.progress-text')).toHaveText(`${i + 2} / 10`);
    }
  }
  await step(page, info, '05_PR-12_结果90百分比', async () => {
    const finished = await responseFor(page, `/api/practice/sessions/${session.id}/finish`, () => tap(page, '看结果'));
    expect(finished.answeredCount).toBe(10); expect(finished.correctCount).toBe(9);
    expect(finished.wrongQuestionIds).toEqual([originalWrong]);
    await expect(page.locator('.rate-num')).toHaveText('90%');
    evidence.finish = finished;
  });
  let retry;
  await step(page, info, '06_WR-01_进入错题本', async () => {
    await button(page, '返回首页').click();
    await tap(page, '错题本');
    await expect(page.locator('.wrongbook .item')).toHaveCount(1);
    await expect(page.locator('.wrongbook .item .bad').first()).toHaveText('错 1 次');
    expect((await api.get('/api/wrong-questions')).items.map(q => q.questionId)).toEqual([originalWrong]);
  });
  await step(page, info, '07_WR-03_重练错题并答对', async () => {
    retry = await responseFor(page, '/api/practice/sessions', () => button(page, '重练全部').click());
    expect(retry.mode).toBe('WRONG'); expect(retry.questionCount).toBe(1);
    expect(retry.questions[0].id).toBe(originalWrong);
    const detail = await api.get(`/api/questions/${originalWrong}`);
    const q = page.locator('.qwrap');
    if (detail.type === 'FILL') {
      for (let j = 0; j < detail.acceptedAnswers.length; j++) await q.locator('textarea').nth(j).fill(detail.acceptedAnswers[j][0]);
    } else {
      for (const key of detail.correctAnswer) await q.locator('.qb-option').filter({ has: page.locator('.qb-key').filter({ hasText: new RegExp(`^${detail.type === 'JUDGE' ? key === 'T' ? '对' : '错' : key}$`) }) }).click();
    }
    expect((await responseFor(page, `/api/practice/sessions/${retry.id}/answers`, () => button(q, '提交答案').click())).correct).toBe(true);
    await expect(q.locator('.ar-verdict')).toHaveText('✓ 回答正确');
  });
  await step(page, info, '08_WR-03_重练结果与连对计数', async () => {
    await tap(page, '看结果');
    await expect(page.locator('.rate-num')).toHaveText('100%');
    const wrong = (await api.get('/api/wrong-questions')).items[0];
    expect(wrong.questionId).toBe(originalWrong); expect(wrong.correctStreak).toBe(1);
    evidence.retrySessionId = retry.id; evidence.correctStreak = wrong.correctStreak;
  });
  expect(evidence.pageErrors).toEqual([]);
  await info.attach('M1 用户走查结果（不含认证数据）', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
});
