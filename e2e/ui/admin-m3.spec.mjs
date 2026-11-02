import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { Api, register, unique, items } from '../lib/http.mjs';
import { ADMIN_URL } from '../lib/stack-urls.mjs';
import { excelFile } from '../lib/admin-fixtures.mjs';
import { step, responseFor } from './support.mjs';

test('QA-RUN-03 管理后台：权限→分类→代码图片题→导入→固定/规则卷→纠错→禁用→看板', async ({ page, context }, info) => {
  test.setTimeout(240000);
  const learner = await register(); const tag = unique('uiadmin');
  const evidence = { main: process.env.QA_MAIN_COMMIT || '00d806c', pageErrors: [], learnerId: learner.user.id };
  page.on('pageerror', error => evidence.pageErrors.push(error.message));
  await context.route('**/*', route => ['localhost', '127.0.0.1', '[::1]'].includes(new URL(route.request().url()).hostname) ? route.continue() : route.abort());
  const menu = name => page.getByRole('menuitem', { name, exact: true }).click();
  const item = (scope, label) => scope.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: new RegExp(`^${label}$`) }) });
  const dialog = () => page.locator('.el-dialog:visible');
  const row = name => page.getByRole('row').filter({ has: page.getByText(name, { exact: true }) });
  const setNumber = async (scope, label, value) => { const input = item(scope, label).getByRole('spinbutton'); await input.fill(String(value)); await input.press('Tab'); };
  let staff, root, subject, chapter, question, uploaded, fixed, rule;

  await step(page, info, '01_AD-01_普通用户后台登录被拒', async () => {
    await page.goto(ADMIN_URL, { waitUntil: 'domcontentloaded' });
    await page.getByLabel('用户名', { exact: true }).fill(learner.credentials.username);
    await page.getByLabel('密码', { exact: true }).fill(learner.credentials.password);
    await responseFor(page, '/api/auth/login', () => page.getByRole('button', { name: '登录', exact: true }).click());
    await expect(page.getByText('这个账号不是管理员，不能登录后台', { exact: true })).toBeVisible();
    await expect(page).toHaveURL(/login/);
    await learner.api.get('/api/admin/dashboard', 403, 'FORBIDDEN');
    evidence.nonAdminRejected = true;
  });
  await step(page, info, '02_AD-01_管理员登录', async () => {
    await page.getByLabel('用户名', { exact: true }).fill(process.env.ADMIN_USERNAME || 'admin');
    await page.getByLabel('密码', { exact: true }).fill(process.env.ADMIN_PASSWORD || 'admin123');
    const auth = await responseFor(page, '/api/auth/login', () => page.getByRole('button', { name: '登录', exact: true }).click());
    staff = new Api(auth.token); expect(auth.user.role).toBe('ADMIN');
    await expect(page.getByRole('menuitem', { name: '分类管理', exact: true })).toBeVisible();
    evidence.dashboardBefore = await staff.get('/api/admin/dashboard');
  });
  await step(page, info, '03_AD-02_UI建立三级分类', async () => {
    await menu('分类管理'); await page.getByRole('button', { name: '新建考试（一级）', exact: true }).click();
    await item(dialog(), '名称').locator('input').fill(tag);
    root = await responseFor(page, '/api/admin/categories', () => dialog().getByRole('button', { name: '保存', exact: true }).click());
    await row(root.name).getByRole('button', { name: '加科目', exact: true }).click();
    await item(dialog(), '名称').locator('input').fill(`${tag}科目`);
    subject = await responseFor(page, '/api/admin/categories', () => dialog().getByRole('button', { name: '保存', exact: true }).click());
    await row(subject.name).getByRole('button', { name: '加章节', exact: true }).click();
    await item(dialog(), '名称').locator('input').fill(`${tag}章节`);
    chapter = await responseFor(page, '/api/admin/categories', () => dialog().getByRole('button', { name: '保存', exact: true }).click());
    expect(chapter.level).toBe(3); expect(chapter.parentId).toBe(subject.id);
    await expect(row(chapter.name)).toBeVisible(); evidence.categoryIds = [root.id, subject.id, chapter.id];
    await learner.api.put('/api/me', { targetCategoryId: root.id });
  });
  await step(page, info, '04_AD-04_AD-12_录入代码题预览并上传图片', async () => {
    await menu('题目管理'); await page.getByRole('button', { name: '新建题目', exact: true }).click();
    await page.getByPlaceholder('选择考试 / 科目 / 章节', { exact: true }).click();
    for (const name of [root.name, subject.name, chapter.name]) await page.locator('.el-cascader__dropdown:visible').getByText(new RegExp(`^${name}（`)).click();
    await page.getByPlaceholder('题干，支持 Markdown 和代码块', { exact: true }).fill(`${tag} UI code：输出是什么？\n\n\`\`\`java\nSystem.out.println(1 + 1);\n\`\`\``);
    await responseFor(page, '/api/admin/questions/preview', () => page.locator('.md-field').first().getByRole('button', { name: '预览', exact: true }).click());
    await expect(page.locator('.md-preview pre code')).toContainText('System.out.println');
    const image = await readFile(new URL('../../app/src/static/tab/home-active.png', import.meta.url));
    uploaded = await responseFor(page, '/api/files', () => page.locator('input[type=file]').setInputFiles({ name: 'qa-home-icon.png', mimeType: 'image/png', buffer: image }));
    expect(uploaded.url).toMatch(/^\/uploads\//);
    await expect.poll(() => page.locator('.thumb img').evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
    evidence.imageUrl = uploaded.url;
  });
  await step(page, info, '05_AD-04_发布题目并核对数据', async () => {
    for (const [index, value] of ['2', '1', '3', '0'].entries()) await page.getByPlaceholder(`选项 ${'ABCD'[index]}（支持 Markdown）`, { exact: true }).fill(value);
    await page.locator('.option .el-radio').filter({ hasText: /^A$/ }).click();
    await page.getByPlaceholder('解析，发布前必填', { exact: true }).fill('整数 1 与 1 相加得到 2，因此选择 A。');
    await page.locator('.el-radio').filter({ hasText: /^发布$/ }).click();
    question = await responseFor(page, '/api/admin/questions', () => page.getByRole('button', { name: '保存', exact: true }).click());
    expect(question.categoryId).toBe(chapter.id); expect(question.imageUrl).toBe(uploaded.url);
    expect(question.correctAnswer).toEqual(['A']); expect(question.status).toBe('PUBLISHED'); evidence.questionId = question.id;
  });
  await step(page, info, '06_AD-14_Excel正确行与错误行部分导入', async () => {
    await menu('Excel 导入');
    const path = `${root.name}/${subject.name}/${chapter.name}`;
    const form = await excelFile([
      [path, '单选', `${tag} 导入单选`, '甲', '乙', '', '', '', '', 'A', '甲是本题指定正确项', 2, 'qa', '原创'],
      [path, '判断', `${tag} 导入判断`, '', '', '', '', '', '', '对', '本题判断为正确', 2, 'qa', '原创'],
      [path, '单选', `${tag} 错误行`, '甲', '乙', '', '', '', '', 'Z', '答案键不在选项中', 2, 'qa', '原创'],
    ]);
    const file = form.get('file');
    const result = await responseFor(page, '/api/admin/questions/import', async () => page.locator('input[type=file]').setInputFiles({ name: 'qa-mixed.xlsx', mimeType: file.type, buffer: Buffer.from(await file.arrayBuffer()) }));
    expect(result.total).toBe(3); expect(result.success).toBe(2); expect(result.failed[0].row).toBe(4);
    await expect(page.getByRole('heading', { name: /导入结果/ })).toBeVisible();
    await expect(page.locator('.failed').getByRole('row').filter({ hasText: result.failed[0].reason })).toBeVisible();
    expect((await staff.get(`/api/admin/questions?categoryId=${chapter.id}`)).total).toBe(3); evidence.import = result;
  });
  const fillPaperHeader = async title => {
    await item(page, '标题').locator('input').fill(title);
    await item(page, '所属考试').locator('.el-select').click();
    await page.locator('.el-select-dropdown:visible').getByText(root.name, { exact: true }).click();
    await setNumber(page, '时长（分钟）', 5); await setNumber(page, '及格分', 3);
  };
  await step(page, info, '07_AD-08_固定卷挑题保存上架', async () => {
    await menu('试卷管理'); await page.getByRole('button', { name: '新建试卷', exact: true }).click(); await fillPaperHeader(`${tag} 固定卷`);
    await page.getByRole('button', { name: '挑题', exact: true }).click();
    await dialog().getByPlaceholder('题干关键词', { exact: true }).fill(`${tag} UI code`);
    await dialog().getByRole('button', { name: '查询', exact: true }).click();
    const picked = dialog().getByRole('row').filter({ hasText: `${tag} UI code` });
    await picked.locator('.el-checkbox').click(); await expect(picked.getByRole('checkbox')).toBeChecked();
    await dialog().getByRole('button', { name: '加入试卷', exact: true }).click();
    fixed = await responseFor(page, '/api/admin/papers', () => page.getByRole('button', { name: '保存', exact: true }).click());
    expect(fixed.totalScore).toBe(5); expect(fixed.questions[0].questionId).toBe(question.id);
    await page.getByRole('button', { name: '返回列表', exact: true }).click();
    expect((await responseFor(page, `/api/admin/papers/${fixed.id}/status`, () => row(fixed.title).getByRole('button', { name: '上架', exact: true }).click(), 'PUT')).status).toBe('ONLINE');
    evidence.fixedPaperId = fixed.id;
  });
  await step(page, info, '08_AD-16_规则卷题量缺口阻止上架', async () => {
    await page.getByRole('button', { name: '新建试卷', exact: true }).click(); await fillPaperHeader(`${tag} 规则卷`);
    await page.getByText('规则卷', { exact: true }).click(); await page.getByRole('button', { name: '加一条规则', exact: true }).click();
    rule = await responseFor(page, '/api/admin/papers', () => page.getByRole('button', { name: '保存', exact: true }).click());
    await page.getByRole('button', { name: '返回列表', exact: true }).click();
    const pending = page.waitForResponse(r => r.request().method() === 'PUT' && new URL(r.url()).pathname === `/api/admin/papers/${rule.id}/status`);
    await row(rule.title).getByRole('button', { name: '上架', exact: true }).click();
    const response = await pending; expect(response.status()).toBe(409); const gap = await response.json();
    expect(gap.code).toBe('POOL_INSUFFICIENT'); expect(gap.data.gaps[0].need).toBe(5); expect(gap.data.gaps[0].available).toBe(2);
    await expect(dialog()).toContainText('题不够'); await expect(dialog()).toContainText('需要'); await expect(dialog()).toContainText('现有');
    evidence.gaps = gap.data.gaps;
  });
  await step(page, info, '09_AD-16_调整规则后上架', async () => {
    await dialog().getByRole('button', { name: '知道了', exact: true }).click();
    await row(rule.title).getByRole('button', { name: '编辑', exact: true }).click();
    const count = item(page, '抽题规则').getByRole('spinbutton').first(); await count.fill('2'); await count.press('Tab');
    rule = await responseFor(page, `/api/admin/papers/${rule.id}`, () => page.getByRole('button', { name: '保存', exact: true }).click(), 'PUT');
    expect(rule.totalScore).toBe(4); await page.getByRole('button', { name: '返回列表', exact: true }).click();
    expect((await responseFor(page, `/api/admin/papers/${rule.id}/status`, () => row(rule.title).getByRole('button', { name: '上架', exact: true }).click(), 'PUT')).status).toBe('ONLINE');
    evidence.rulePaperId = rule.id;
  });
  await step(page, info, '10_AD-18_处理学员纠错', async () => {
    const feedback = await learner.api.post(`/api/questions/${question.id}/feedback`, { type: 'ANALYSIS_UNCLEAR', content: `${tag} 请补充代码解释` }, 201);
    await menu('纠错处理');
    await page.getByRole('row').filter({ hasText: feedback.content }).getByRole('button', { name: '处理', exact: true }).click();
    await item(dialog(), '回复').locator('textarea').fill('已核对：1 加 1 得到 2。');
    await responseFor(page, `/api/admin/feedback/${feedback.id}`, () => dialog().getByRole('button', { name: '提交', exact: true }).click(), 'PUT');
    await page.getByText('已采纳', { exact: true }).click();
    await expect(page.getByRole('row').filter({ hasText: feedback.content })).toContainText('已核对'); evidence.feedbackId = feedback.id;
  });
  await step(page, info, '11_AD-19_禁用用户后登录和旧令牌被拒', async () => {
    const sheet = await learner.api.post(`/api/exams/${fixed.id}/start`, {}, 201);
    await learner.api.put(`/api/exam-records/${sheet.recordId}/answers/${question.id}`, { userAnswer: ['A'], version: 1, marked: false });
    const result = await learner.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
    expect(result.items[0].imageUrl).toBe(uploaded.url); expect(result.items[0].blankCount).toBe(0); evidence.examResult = result;
    await menu('用户管理'); await page.getByPlaceholder('用户名或昵称', { exact: true }).fill(learner.credentials.username);
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await row(learner.credentials.username).getByRole('button', { name: '禁用', exact: true }).click();
    const confirm = page.locator('.el-message-box:visible').getByRole('button', { name: '禁用', exact: true });
    await responseFor(page, `/api/admin/users/${learner.user.id}/status`, () => confirm.click(), 'PUT');
    await expect(row(learner.credentials.username)).toContainText('已禁用');
    await new Api().post('/api/auth/login', learner.credentials, 403, 'FORBIDDEN'); await learner.api.get('/api/me', 403, 'FORBIDDEN');
    evidence.disabledDenied = true;
  });
  await step(page, info, '12_AD-22_看板五项和试卷统计对账', async () => {
    await menu('数据看板'); const dashboard = await staff.get('/api/admin/dashboard');
    expect(dashboard.userCount).toBe(evidence.dashboardBefore.userCount);
    expect(dashboard.questionCount).toBe(evidence.dashboardBefore.questionCount + 3);
    expect(dashboard.paperCount).toBe(evidence.dashboardBefore.paperCount + 2);
    expect(dashboard.todayAnswerCount).toBe(evidence.dashboardBefore.todayAnswerCount + 1);
    expect(dashboard.pendingFeedbackCount).toBe(evidence.dashboardBefore.pendingFeedbackCount);
    for (const [label, key] of Object.entries({ 用户数: 'userCount', 题目数: 'questionCount', 试卷数: 'paperCount', 今日作答: 'todayAnswerCount', 待处理纠错: 'pendingFeedbackCount' })) {
      const card = page.locator('.stat').filter({ has: page.getByText(label, { exact: true }) });
      await expect(card.locator('.stat-num')).toHaveText(String(dashboard[key]));
    }
    const stats = dashboard.papers.find(p => p.paperId === fixed.id); expect(stats.attempts).toBe(1); expect(stats.avgScore).toBe(5); expect(stats.passRate).toBe(1);
    await expect(row(fixed.title)).toContainText('100%'); evidence.dashboard = dashboard;
  });
  expect(evidence.pageErrors).toEqual([]);
  await info.attach('后台 M3 走查结果（无认证数据）', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' });
});
