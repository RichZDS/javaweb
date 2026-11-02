import { test, expect } from '@playwright/test';
import { fixture } from '../lib/fixtures.mjs';
import { step, button, tap, responseFor, selectCategory } from './support.mjs';

test('P0 后台主流程：登录→分类→题目→固定卷上架', async ({ page }, info) => {
  const f = await fixture({ count: 0 });
  await step(page, info, '01_AD-01_管理员登录', async () => {
    await page.goto('/');
    await page.getByPlaceholder(/用户名/).fill(process.env.ADMIN_USERNAME || 'admin');
    await page.getByPlaceholder(/密码/).fill(process.env.ADMIN_PASSWORD || 'admin123');
    await responseFor(page, '/api/auth/login', () => button(page, '登录').click());
    await expect(page.getByText('分类管理', { exact: true })).toBeVisible();
  });
  await step(page, info, '02_AD-02_新建分类', async () => {
    await tap(page, '分类管理');
    await button(page, '新增分类|新建分类').click();
    await page.getByLabel('名称', { exact: true }).fill(`${f.tag} UI分类`);
    const created = await responseFor(page, '/api/admin/categories', () => button(page, '保存|确定').click());
    expect(created.name).toBe(`${f.tag} UI分类`);
  });
  let question;
  await step(page, info, '03_AD-04_新建题目', async () => {
    await tap(page, '题目管理');
    await button(page, '新增题目|新建题目').click();
    await selectCategory(page, '分类', f);
    await page.getByLabel('题干', { exact: true }).fill(`${f.tag} UI原创题`);
    await page.getByLabel('选项A', { exact: true }).fill('甲');
    await page.getByLabel('选项B', { exact: true }).fill('乙');
    await page.getByLabel('正确答案', { exact: true }).fill('A');
    await page.getByLabel('解析', { exact: true }).fill('根据题目定义，甲是正确项。');
    await page.getByLabel('来源', { exact: true }).fill('原创');
    await page.getByLabel('状态', { exact: true }).click();
    await tap(page, '已发布');
    question = await responseFor(page, '/api/admin/questions', () => button(page, '保存|确定').click());
    expect(question.status).toBe('PUBLISHED');
    expect(question.correctAnswer).toEqual(['A']);
  });
  await step(page, info, '04_AD-08_新建固定卷并上架', async () => {
    await tap(page, '试卷管理');
    await button(page, '新增试卷|新建试卷').click();
    await page.getByLabel('标题', { exact: true }).fill(`${f.tag} UI固定卷`);
    await selectCategory(page, '所属考试', f, false);
    await page.getByLabel('时长', { exact: true }).fill('5');
    await page.getByLabel('及格分', { exact: true }).fill('1');
    await button(page, '添加题目|选择题目').click();
    await page.getByPlaceholder(/搜索|关键词/).fill(`${f.tag} UI原创题`);
    await button(page, '搜索|查询').click();
    const row = page.getByRole('row').filter({ hasText: `${f.tag} UI原创题` });
    await row.getByRole('checkbox').check();
    await button(page, '确认选择|确定').click();
    const created = await responseFor(page, '/api/admin/papers', () => button(page, '保存|确定').click());
    expect(created.questions.some(q => q.questionId === question.id)).toBe(true);
    const paperRow = page.getByRole('row').filter({ hasText: created.title });
    await paperRow.getByRole('button', { name: '上架', exact: true }).click();
    if (await button(page, '确定').isVisible()) await button(page, '确定').click();
    await expect.poll(async () => (await f.staff.get(`/api/admin/papers/${created.id}`)).status).toBe('ONLINE');
  });
});
