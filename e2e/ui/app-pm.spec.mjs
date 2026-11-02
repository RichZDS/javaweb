import { test, expect } from '@playwright/test';
import { fixture, practice, answer, incorrect } from '../lib/fixtures.mjs';
import { APP_URL } from '../lib/stack-urls.mjs';
import { step, tap, button, responseFor } from './support.mjs';

test('QA-RUN-03 App PM-M3：目标考试错题范围、结束练习只读、切目标分类失败不混旧数据', async ({ page, context }, info) => {
  test.setTimeout(150000);
  const a = await fixture({ count: 2, types: ['SINGLE'] });
  const b = await fixture({ count: 2, types: ['SINGLE'] });
  a.subject = await a.staff.put(`/api/admin/categories/${a.subject.id}`, { name: `${a.tag}A科目`, sort: 0 });
  b.subject = await b.staff.put(`/api/admin/categories/${b.subject.id}`, { name: `${b.tag}B科目`, sort: 0 });
  const source = await practice(a, { acceptFewer: true }); const qA = source.questions[0];
  await answer(a, source, qA, incorrect(qA)); await a.api.post(`/api/practice/sessions/${source.id}/finish`, {});
  await a.api.put('/api/favorites/' + qA.id, { note: 'A考试收藏' });
  await a.api.put('/api/me', { targetCategoryId: b.root.id });
  const sourceB = await a.api.post('/api/practice/sessions', { mode: 'RANDOM', categoryIds: [b.root.id], count: 10, acceptFewer: true }, 201);
  const qB = sourceB.questions[0];
  await answer(a, sourceB, qB, incorrect(qB)); await a.api.post(`/api/practice/sessions/${sourceB.id}/finish`, {});
  await a.api.put('/api/favorites/' + qB.id, { note: 'B考试收藏' });
  await a.api.put('/api/me', { targetCategoryId: a.root.id });
  const evidence = { main: process.env.QA_MAIN_COMMIT || '00d806c', userId: a.user.id, roots: [a.root.id, b.root.id], sourceSessionId: source.id, wrongIds: [qA.id, qB.id], pageErrors: [] };
  page.on('pageerror', error => evidence.pageErrors.push(error.message));
  await context.route('**/*', route => ['localhost', '127.0.0.1', '[::1]'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
  await step(page, info, '01_登录并确认A考试', async () => {
    await page.goto(APP_URL, { waitUntil: 'domcontentloaded' });
    const field = label => page.locator('.field').filter({ has: page.locator('.label').filter({ hasText: new RegExp(`^${label}$`) }) }).locator('input');
    await field('用户名').fill(a.credentials.username); await field('密码').fill(a.credentials.password);
    await responseFor(page, '/api/auth/login', () => button(page, '登录').click());
    await expect(page.locator('.hero-exam')).toHaveText(a.root.name);
    await expect(page.locator('.home .subject-name')).toHaveText(a.subject.name);
  });
  await step(page, info, '02_PM-M3-01_A错题列表分组与重练一致', async () => {
    await tap(page, '错题本'); await expect(page.locator('.scope-name')).toHaveText(a.root.name);
    await expect(page.locator('.wrongbook .item')).toHaveCount(1);
    expect((await a.api.get('/api/wrong-questions')).items.map(q => q.questionId)).toEqual([qA.id]);
    expect((await a.api.get('/api/wrong-questions/groups')).map(g => g.categoryId)).toEqual([a.chapter.id]);
    expect((await a.api.get('/api/favorites')).items.map(q => q.questionId)).toEqual([qA.id]);
    const retried = await responseFor(page, '/api/practice/sessions', () => button(page, '重练全部').click());
    expect(retried.questions.map(q => q.id)).toEqual([qA.id]); evidence.retryA = retried.id;
    await expect(page.locator('.progress-text')).toHaveText('1 / 1');
  });
  await step(page, info, '03_PM-M3-01_切B后列表与重练同步切换', async () => {
    await page.goBack(); await page.locator('.scope-link').click();
    await responseFor(page, '/api/me', () => tap(page, b.root.name), 'PUT');
    await expect(page.locator('.hero-exam')).toHaveText(b.root.name); await tap(page, '错题本');
    await expect(page.locator('.scope-name')).toHaveText(b.root.name); await expect(page.locator('.wrongbook .item')).toHaveCount(1);
    expect((await a.api.get('/api/wrong-questions')).items.map(q => q.questionId)).toEqual([qB.id]);
    expect((await a.api.get('/api/wrong-questions/groups')).map(g => g.categoryId)).toEqual([b.chapter.id]);
    expect((await a.api.get('/api/favorites')).items.map(q => q.questionId)).toEqual([qB.id]);
    const retried = await responseFor(page, '/api/practice/sessions', () => button(page, '重练全部').click());
    expect(retried.questions.map(q => q.id)).toEqual([qB.id]); evidence.retryB = retried.id;
  });
  await step(page, info, '04_PM-M3-02_从记录打开已结束练习', async () => {
    await page.goBack(); await page.locator('.scope-link').click();
    await responseFor(page, '/api/me', () => tap(page, a.root.name), 'PUT');
    await expect(page.locator('.hero-exam')).toHaveText(a.root.name); await tap(page, '我的');
    const pending = page.waitForResponse(r => new URL(r.url()).pathname === '/api/practice/sessions' && r.request().method() === 'GET');
    await tap(page, '练习记录'); const records = (await (await pending).json()).data.items;
    const index = records.findIndex(r => r.id === source.id); expect(index).toBeGreaterThanOrEqual(0);
    await page.locator('.rec').nth(index).click(); await button(page, '查看全部解析').click();
    await expect(page.locator('.ended-bar')).toContainText('已结束');
    await expect(page.locator('.top-btn.strong')).toHaveText('返回结果');
  });
  await step(page, info, '05_PM-M3-02_未答只读且不再提交', async () => {
    await tap(page.locator('.bottombar'), '下一题');
    const q = page.locator('.qwrap').filter({ has: page.getByText('第 2 题', { exact: true }) });
    await expect(q.getByText('未作答', { exact: true })).toBeVisible();
    await expect(button(q, '提交答案')).toHaveCount(0);
    let sent = 0; const listener = request => { if (new URL(request.url()).pathname === `/api/practice/sessions/${source.id}/answers` && request.method() === 'POST') sent++; };
    page.on('request', listener); await q.locator('.qb-option').first().click();
    await expect(page.locator('.top-btn.strong')).toHaveText('返回结果');
    const stored = await a.api.get(`/api/practice/sessions/${source.id}`);
    expect(stored.answeredCount).toBe(1); expect(stored.questions[1].submitted).toBe(false); expect(sent).toBe(0);
    page.off('request', listener); evidence.endedReadonly = true;
  });
  await step(page, info, '06_PM-M3-02_返回原结果不改判', async () => {
    await tap(page, '返回结果'); await expect(page.locator('.rate-num')).toHaveText('0%');
    const stored = await a.api.get(`/api/practice/sessions/${source.id}`); expect(stored.answeredCount).toBe(1); expect(stored.correctCount).toBe(0);
    await button(page, '返回首页').click(); await expect(page.locator('.home .subject-name')).toHaveText(a.subject.name);
  });
  let failCategory = true;
  const failRoute = async route => {
    const url = new URL(route.request().url());
    if (failCategory && url.pathname === '/api/categories' && url.searchParams.get('rootId') === String(b.root.id)) {
      evidence.injectedFailure = url.pathname + url.search; return route.abort('failed');
    }
    return route.continue();
  };
  await page.route('**/api/categories?*', failRoute);
  await step(page, info, '07_PM-M3-03_切目标分类失败不残留A科目', async () => {
    await page.locator('.hero-switch').click(); await responseFor(page, '/api/me', () => tap(page, b.root.name), 'PUT');
    await expect(page.locator('.hero-exam')).toHaveText(b.root.name);
    await expect(page.getByText(`${b.root.name}的科目没加载出来`, { exact: true })).toBeVisible();
    await expect(page.locator('.home .subject-name')).toHaveCount(0);
    await expect(page.locator('.home .err-reason')).not.toHaveText('');
    await expect(button(page.locator('.home'), '重试')).toBeVisible(); evidence.oldSubjectsCleared = true;
  });
  await step(page, info, '08_PM-M3-03_恢复请求重试只显示B科目', async () => {
    failCategory = false; await button(page.locator('.home'), '重试').click();
    await expect(page.locator('.home .subject-name')).toHaveText(b.subject.name);
    await expect(page.locator('.home').getByText(a.subject.name, { exact: true })).toHaveCount(0);
    expect((await a.api.get('/api/me')).targetCategoryId).toBe(b.root.id); evidence.retryShowsNewTarget = true;
  });
  await page.unroute('**/api/categories?*', failRoute);
  expect(evidence.pageErrors).toEqual([]);
  await info.attach('PM-M3 三项复测（无认证数据）', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
});
