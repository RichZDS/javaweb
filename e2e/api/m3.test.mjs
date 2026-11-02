import assert from 'node:assert/strict';
import ExcelJS from 'exceljs';
import { apiSuite } from '../lib/api-suite.mjs';
import { Api, BASE_URL, admin, unique, items, approx } from '../lib/http.mjs';
import { fixture, paper, practice, answer } from '../lib/fixtures.mjs';
import { clientError, findItem, paperBody, excelFile, excelHeaders } from '../lib/admin-fixtures.mjs';

const scenario = await apiSuite('M3');

scenario('AD-01 NF-01：普通用户管理端读/写/删均403，管理员可读取', async () => {
  const f = await fixture({ count: 1 });
  for (const path of ['categories', 'questions', 'papers', 'users', 'feedback', 'dashboard']) await f.api.get(`/api/admin/${path}`, 403, 'FORBIDDEN');
  await f.api.post('/api/admin/categories', { parentId: null, name: unique(), sort: 0 }, 403, 'FORBIDDEN');
  await f.api.put(`/api/admin/questions/${f.questions[0].id}`, f.questions[0].fixtureBody, 403, 'FORBIDDEN');
  await f.api.delete(`/api/admin/questions/${f.questions[0].id}`, 403, 'FORBIDDEN');
  assert.equal((await f.staff.get(`/api/admin/questions/${f.questions[0].id}`)).id, f.questions[0].id);
});

scenario('AD-02/03：三级分类增改排删，第四级拒绝，有子项/题409', async () => {
  const f = await fixture({ count: 1 });
  await f.staff.post('/api/admin/categories', { parentId: f.chapter.id, name: '第四级', sort: 0 }, 400, 'VALIDATION_FAILED');
  await f.staff.delete(`/api/admin/categories/${f.root.id}`, 409, 'CONFLICT');
  await f.staff.delete(`/api/admin/categories/${f.chapter.id}`, 409, 'CONFLICT');
  const empty = await f.staff.post('/api/admin/categories', { parentId: f.subject.id, name: unique(), sort: 2 }, 201);
  const changed = await f.staff.put(`/api/admin/categories/${empty.id}`, { name: '验收改名章节', sort: 9 });
  assert.equal(changed.level, 3); assert.equal(changed.sort, 9); assert.equal(changed.name, '验收改名章节');
  assert.equal(await f.staff.delete(`/api/admin/categories/${empty.id}`), null);
  const tree = await f.staff.get('/api/admin/categories');
  const flatten = nodes => nodes.flatMap(n => [n, ...flatten(n.children || [])]);
  assert.ok(!flatten(tree).some(n => n.id === empty.id));
});

scenario('AD-04/05/06/11：四题型字段、发布校验、章节限制、编辑版本和筛选', async () => {
  const f = await fixture({ count: 4 });
  const judge = f.questions.find(q => q.type === 'JUDGE'); const fill = f.questions.find(q => q.type === 'FILL');
  assert.deepEqual(judge.options.map(o => o.key), ['T', 'F']); assert.equal(fill.blankCount, 2);
  assert.equal(fill.options.length, 0); assert.deepEqual(fill.acceptedAnswers, [['int', 'Integer'], ['5']]);
  for (const patch of [{ analysis: '' }, { correctAnswer: [] }, { categoryId: f.root.id }]) {
    await f.staff.post('/api/admin/questions', { ...f.questions[0].fixtureBody, ...patch }, 400, 'VALIDATION_FAILED');
  }
  const q = f.questions[0];
  const changed = await f.staff.put(`/api/admin/questions/${q.id}`, { ...q.fixtureBody, stem: `${f.tag} 编辑后的题` });
  assert.equal(changed.version, q.version + 1);
  const found = await f.staff.get(`/api/admin/questions?categoryId=${f.chapter.id}&type=SINGLE&status=PUBLISHED&keyword=${f.tag}&page=1&size=1`);
  assert.equal(found.total, 1); assert.equal(found.items[0].id, q.id); assert.equal(found.items[0].version, changed.version);
  assert.equal(found.items[0].answerCount, 0); assert.equal(found.items[0].correctRate, null);
});

scenario('AD-07 CA-04：草稿不可见，未使用题真删，已作答题归档并保留历史', async () => {
  const f = await fixture({ count: 1 }); const q = f.questions[0];
  const draft = await f.staff.post('/api/admin/questions', { ...q.fixtureBody, stem: `${f.tag} 草稿`, status: 'DRAFT' }, 201);
  await f.api.get(`/api/questions/${draft.id}`, 404, 'NOT_FOUND');
  assert.deepEqual(await f.staff.delete(`/api/admin/questions/${draft.id}`), { deleted: true, archived: false });
  await f.staff.get(`/api/admin/questions/${draft.id}`, 404, 'NOT_FOUND');
  const s = await practice(f, { acceptFewer: true }); await answer(f, s, q);
  assert.deepEqual(await f.staff.delete(`/api/admin/questions/${q.id}`), { deleted: false, archived: true });
  assert.equal((await f.staff.get(`/api/admin/questions/${q.id}`)).status, 'ARCHIVED');
  await f.api.get(`/api/questions/${q.id}`, 404, 'NOT_FOUND');
  assert.equal((await f.api.get(`/api/questions/search?keyword=${f.tag}&categoryId=${f.root.id}`)).total, 0);
  assert.equal((await f.api.get(`/api/practice/sessions/${s.id}`)).correctCount, 1);
});

scenario('CA-09 AD-11：预览保留代码块、过滤执行型标签/事件/javascript链接', async () => {
  const staff = await admin();
  const { html } = await staff.post('/api/admin/questions/preview', {
    markdown: '<script>alert(1)</script>\n<img src=x onerror=alert(1)>\n[x](javascript:alert(1))\n\n```java\nint count = 1;\n```',
  });
  assert.match(html, /<pre[\s>]/); assert.match(html, /<code[\s>]/);
  assert.doesNotMatch(html, /<script\b|<[^>]+\sonerror\s*=|href\s*=\s*["']\s*javascript:/i);
});

scenario('AD-08/09：固定卷自动算分、空卷/过高及格线上架拒绝、上下架与删除', async () => {
  const f = await fixture({ count: 4 });
  const p = await paper(f, { totalScore: 999 }, false); assert.equal(p.totalScore, 12);
  const on = await f.staff.put(`/api/admin/papers/${p.id}/status`, { status: 'ONLINE' }); assert.equal(on.status, 'ONLINE');
  assert.ok((await f.api.get('/api/exams')).some(x => x.paperId === p.id));
  await f.staff.put(`/api/admin/papers/${p.id}/status`, { status: 'OFFLINE' });
  assert.ok(!(await f.api.get('/api/exams')).some(x => x.paperId === p.id));
  for (const overrides of [{ questions: [], passScore: 1 }, { passScore: 13 }]) {
    // The contract forbids publication but does not require saving an invalid draft.
    const body = paperBody(p, { ...overrides, title: unique('invalid') });
    const response = await fetch(`${BASE_URL}/api/admin/papers`, { method: 'POST',
      headers: { Authorization: `Bearer ${f.staff.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body), signal: AbortSignal.timeout(10000) });
    const payload = await response.json();
    if (response.status === 201) {
      assert.equal(payload.code, 'OK'); const invalid = payload.data;
      await clientError(f.staff, 'PUT', `/api/admin/papers/${invalid.id}/status`, { status: 'ONLINE' });
      assert.equal((await f.staff.get(`/api/admin/papers/${invalid.id}`)).status, 'OFFLINE');
    } else {
      assert.ok(response.status >= 400 && response.status < 500); assert.notEqual(payload.code, 'OK');
    }
    assert.ok(!(await f.api.get('/api/exams')).some(x => x.title === body.title));
  }
  await f.staff.delete(`/api/admin/papers/${p.id}`); await f.staff.get(`/api/admin/papers/${p.id}`, 404, 'NOT_FOUND');
});

scenario('AD-10：已考卷只改标题描述/上下架，拒绝修改结构和删除', async () => {
  const f = await fixture({ count: 4 }); const p = await paper(f);
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  const updated = await f.staff.put(`/api/admin/papers/${p.id}`, paperBody(p, { title: `${f.tag} 新标题`, description: '更新说明' }));
  assert.equal(updated.title, `${f.tag} 新标题`);
  await clientError(f.staff, 'PUT', `/api/admin/papers/${p.id}`, paperBody(updated, { durationMin: p.durationMin + 1 }));
  await clientError(f.staff, 'DELETE', `/api/admin/papers/${p.id}`);
  assert.equal((await f.staff.get(`/api/admin/papers/${p.id}`)).durationMin, p.durationMin);
  await f.staff.put(`/api/admin/papers/${p.id}/status`, { status: 'OFFLINE' });
});

scenario('AD-16 EX-21/22：规则卷跨级/交叉规则不重题，上架和开考都校验缺口', async () => {
  const f = await fixture({ count: 8, types: ['SINGLE'] });
  const rules = [{ categoryId: f.root.id, type: 'SINGLE', count: 4, score: 2 }, { categoryId: f.chapter.id, type: 'SINGLE', count: 3, score: 2 }];
  const p = await paper(f, { mode: 'RULE', questions: undefined, rules });
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  assert.equal(sheet.questions.length, 7); assert.equal(new Set(sheet.questions.map(q => q.questionId)).size, 7);
  assert.equal(sheet.totalScore, 14); await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  const tooMany = await paper(f, { mode: 'RULE', questions: undefined, rules: [{ categoryId: f.chapter.id, type: 'SINGLE', count: 10, score: 2 }] }, false);
  const gaps = await f.staff.put(`/api/admin/papers/${tooMany.id}/status`, { status: 'ONLINE' }, 409, 'POOL_INSUFFICIENT');
  assert.ok(gaps.gaps.some(g => g.need === 10 && g.available === 8 && g.categoryId === f.chapter.id));
  for (const q of f.questions.slice(0, 3)) await f.staff.delete(`/api/admin/questions/${q.id}`);
  const startGap = await f.api.post(`/api/exams/${p.id}/start`, {}, 409, 'POOL_INSUFFICIENT');
  assert.ok(startGap.gaps.length > 0);
});

scenario('AD-08/16：固定卷题目和规则分类必须属于试卷所属考试', async () => {
  const a = await fixture({ count: 1 }); const b = await fixture({ count: 1 });
  await a.staff.post('/api/admin/papers', { title: unique(), categoryId: a.root.id, mode: 'FIXED', durationMin: 5, passScore: 1,
    questions: [{ questionId: b.questions[0].id, score: 1, sort: 0 }] }, 400, 'VALIDATION_FAILED');
  await a.staff.post('/api/admin/papers', { title: unique(), categoryId: a.root.id, mode: 'RULE', durationMin: 5, passScore: 1,
    rules: [{ categoryId: b.root.id, type: 'SINGLE', count: 1, score: 1 }] }, 400, 'VALIDATION_FAILED');
});

scenario('EX-17/18：开考快照不受后续改题/归档影响，旧成绩单仍可读取', async () => {
  const f = await fixture({ count: 1 }); const q = f.questions[0]; const p = await paper(f, { passScore: 3 });
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  await f.staff.put(`/api/admin/questions/${q.id}`, { ...q.fixtureBody, stem: '改后的题干', correctAnswer: ['B'], analysis: '改后的解析' });
  await f.api.put(`/api/exam-records/${sheet.recordId}/answers/${q.id}`, { userAnswer: ['A'], version: 1, marked: false });
  const result = await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  assert.equal(result.score, 3); assert.deepEqual(result.items[0].correctAnswer, ['A']);
  assert.equal(result.items[0].stemHtml, sheet.questions[0].stemHtml);
  assert.doesNotMatch(result.items[0].analysisHtml, /改后的解析/);
  await f.staff.delete(`/api/admin/questions/${q.id}`);
  assert.deepEqual(await f.api.get(`/api/exam-records/${sheet.recordId}`), result);
});

scenario('AD-17/18：纠错列表、解决/拒绝状态、回复和处理时间', async () => {
  const f = await fixture({ count: 1 });
  for (const status of ['RESOLVED', 'REJECTED']) {
    const created = await f.api.post(`/api/questions/${f.questions[0].id}/feedback`, { type: 'OTHER', content: `${f.tag} 纠错 ${status}` }, 201);
    const pending = await findItem(f.staff, '/api/admin/feedback?status=PENDING', x => x.id === created.id);
    assert.ok(pending); assert.equal(pending.username, f.credentials.username);
    await f.staff.put(`/api/admin/feedback/${created.id}`, { status, reply: `处理 ${status}` });
    const handled = await findItem(f.staff, `/api/admin/feedback?status=${status}`, x => x.id === created.id);
    assert.equal(handled.reply, `处理 ${status}`); assert.ok(handled.handledAt);
  }
});

scenario('AD-19/20/21：禁用影响登录和旧令牌，启用保留记录，不能禁用自己', async () => {
  const f = await fixture({ count: 1 }); const s = await practice(f, { acceptFewer: true }); await answer(f, s, f.questions[0]);
  const user = await findItem(f.staff, `/api/admin/users?keyword=${f.credentials.username}`, x => x.id === f.user.id);
  assert.equal(user.answerCount, 1); assert.ok(user.lastActiveAt);
  await f.staff.put(`/api/admin/users/${f.user.id}/status`, { status: 'DISABLED' });
  await new Api().post('/api/auth/login', f.credentials, 403, 'FORBIDDEN'); await f.api.get('/api/me', 403, 'FORBIDDEN');
  await f.staff.put(`/api/admin/users/${f.user.id}/status`, { status: 'ACTIVE' });
  const auth = await new Api().post('/api/auth/login', f.credentials); const restored = new Api(auth.token);
  assert.equal((await restored.get(`/api/practice/sessions/${s.id}`)).correctCount, 1);
  const me = await f.staff.get('/api/me');
  await clientError(f.staff, 'PUT', `/api/admin/users/${me.id}/status`, { status: 'DISABLED' });
  assert.equal((await f.staff.get('/api/me')).status, 'ACTIVE');
});

scenario('AD-13/14：Excel 四题型和错误行混合，仅导入四个合法行', async () => {
  const f = await fixture({ count: 0 }); const path = `${f.root.name}/${f.subject.name}/${f.chapter.name}`;
  const row = (type, suffix, answer, category = path) => [category, type, `${f.tag} ${suffix}`,
    ...(['单选', '多选'].includes(type) ? ['甲', '乙', '丙', '', '', ''] : Array(6).fill('')),
    answer, '原创解析', 2, 'qa,导入', '原创'];
  const result = await f.staff.post('/api/admin/questions/import', await excelFile([
    row('单选', 'single', 'A'), row('多选', 'multi', 'AC'), row('判断', 'judge', '对'), row('填空', 'fill', 'int|Integer||5'),
    row('单选', 'bad-key', 'Z'), row('单选', 'bad-category', 'A', `${f.tag}/不存在/章节`),
  ]));
  assert.equal(result.total, 6); assert.equal(result.success, 4);
  assert.deepEqual(result.failed.map(x => x.row).sort((a, b) => a - b), [6, 7]);
  assert.ok(result.failed.every(x => typeof x.reason === 'string' && x.reason));
  const imported = items(await f.staff.get(`/api/admin/questions?categoryId=${f.chapter.id}&keyword=${f.tag}&size=100`));
  assert.equal(imported.length, 4);
  assert.deepEqual(imported.map(q => q.type).sort(), ['FILL', 'JUDGE', 'MULTIPLE', 'SINGLE']);
  const fill = await f.staff.get(`/api/admin/questions/${imported.find(q => q.type === 'FILL').id}`);
  assert.deepEqual(fill.acceptedAnswers, [['int', 'Integer'], ['5']]); assert.equal(fill.blankCount, 2);
  assert.deepEqual(imported.find(q => q.type === 'JUDGE').correctAnswer, ['T']);
  assert.ok(imported.every(q => q.source === '原创' && q.tags.includes('导入')));
});

scenario('AD-13/15：Excel 模板表头、非xlsx和超5MB文件被拒且不增加题目', async () => {
  const f = await fixture({ count: 0 });
  const response = await fetch(`${BASE_URL}/api/admin/questions/import/template`, { headers: { Authorization: `Bearer ${f.staff.token}` }, signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, 200);
  const workbook = new ExcelJS.Workbook(); await workbook.xlsx.load(Buffer.from(await response.arrayBuffer()));
  assert.deepEqual(workbook.worksheets[0].getRow(1).values.slice(1), excelHeaders);
  const before = (await f.staff.get('/api/admin/questions?size=1')).total;
  for (const [name, bytes] of [['bad.txt', Buffer.from('not xlsx')], ['large.xlsx', Buffer.alloc(5 * 1024 * 1024 + 1)]]) {
    const form = new FormData(); form.set('file', new Blob([bytes]), name);
    await clientError(f.staff, 'POST', '/api/admin/questions/import', form);
  }
  assert.equal((await f.staff.get('/api/admin/questions?size=1')).total, before);
});

scenario('AD-22：看板作答/待处理增量、14日序列、试卷次数均分及格率', async () => {
  const f = await fixture({ count: 1 }); const p = await paper(f, { passScore: 3 });
  const before = await f.staff.get('/api/admin/dashboard');
  const sheet = await f.api.post(`/api/exams/${p.id}/start`, {}, 201);
  await f.api.put(`/api/exam-records/${sheet.recordId}/answers/${f.questions[0].id}`, { userAnswer: ['A'], version: 1, marked: false });
  await f.api.post(`/api/exam-records/${sheet.recordId}/submit`, {});
  await f.api.post(`/api/questions/${f.questions[0].id}/feedback`, { type: 'OTHER', content: '看板计数验收' }, 201);
  const after = await f.staff.get('/api/admin/dashboard');
  assert.equal(after.todayAnswerCount, before.todayAnswerCount + 1);
  assert.equal(after.pendingFeedbackCount, before.pendingFeedbackCount + 1); assert.equal(after.daily.length, 14);
  const stats = after.papers.find(x => x.paperId === p.id);
  assert.equal(stats.attempts, 1); assert.equal(stats.avgScore, 3); approx(stats.passRate, 1);
});
