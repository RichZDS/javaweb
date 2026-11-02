import assert from 'node:assert/strict';
import test from 'node:test';
import { Api, available, serviceError, register, password, items, noAnswers, approx } from '../lib/http.mjs';
import { m1Fixture, startM1 } from '../lib/m1-fixtures.mjs';
import { answer, correct, incorrect } from '../lib/fixtures.mjs';

const online = await available();
function scenario(name, run) { if (online) test(name, { timeout: 60000 }, run); }
if (!online) test('M1 前置：本地 API 服务已启动', () => {
  const error = new Error(serviceError()); error.stack = error.message; throw error;
});

scenario('AC-01/02/04 NF-02：注册登录、重复用户名、错密、缺失/无效令牌', async () => {
  const f = await register();
  const logged = await new Api().post('/api/auth/login', f.credentials);
  assert.equal(logged.user.id, f.user.id);
  assert.equal((await new Api(logged.token).get('/api/me')).username, f.credentials.username);
  await new Api().post('/api/auth/register', f.credentials, 409, 'USERNAME_TAKEN');
  await new Api().post('/api/auth/login', { ...f.credentials, password: password() }, 401, 'UNAUTHORIZED');
  for (const path of ['/api/me', '/api/categories', '/api/practice/sessions/current', '/api/wrong-questions']) {
    await new Api().get(path, 401, 'UNAUTHORIZED');
  }
  await new Api('invalid-token').get('/api/me', 401, 'UNAUTHORIZED');
  assert.equal((await new Api().get('/api/app/version?platform=android')).platform, 'android');
});

scenario('AC-03/11/14：注册字段校验、昵称和密码修改', async () => {
  await new Api().post('/api/auth/register', { username: 'a', password: 'tiny' }, 400, 'VALIDATION_FAILED');
  const f = await register();
  assert.equal((await f.api.put('/api/me', { nickname: '验收同学' })).nickname, '验收同学');
  const next = password();
  const invalid = await f.api.put('/api/me/password', { oldPassword: password(), newPassword: next }, 400, 'VALIDATION_FAILED');
  assert.ok(invalid.fields.some(x => x.field === 'oldPassword'));
  assert.equal(await f.api.put('/api/me/password', { oldPassword: f.credentials.password, newPassword: next }), null);
  await new Api().post('/api/auth/login', f.credentials, 401, 'UNAUTHORIZED');
  assert.equal((await new Api().post('/api/auth/login', { username: f.credentials.username, password: next })).user.id, f.user.id);
});

scenario('AC-08/09 CA-02/06：目标分类、三级树、搜索与分页补充约定', async () => {
  const f = await m1Fixture('four');
  const tree = await f.api.get(`/api/categories?rootId=${f.root.id}`);
  assert.equal(tree.id, f.root.id);
  assert.equal(tree.children[0].children.find(n => n.id === f.chapter.id).questionCount, 4);
  assert.equal(tree.doneCount, 0); assert.equal(tree.correctRate, null);
  await f.api.put('/api/me', { targetCategoryId: f.chapter.id }, 400, 'VALIDATION_FAILED');
  const searched = await f.api.get(`/api/questions/search?keyword=QA%20four&categoryId=${f.chapter.id}&page=0&size=101`);
  assert.equal(searched.page, 1); assert.equal(searched.size, 100); assert.equal(searched.total, 4);
  for (const q of items(searched)) {
    assert.ok(q.stemPreview.length <= 80);
    const detail = await f.api.get(`/api/questions/${q.id}`);
    assert.equal(detail.categoryId, f.chapter.id);
    assert.ok(detail.correctAnswer || detail.acceptedAnswers);
    assert.equal(typeof detail.analysisHtml, 'string');
  }
});

scenario('CA-06 QA-001：搜题关键词必填，缺失和空串返回 400', async () => {
  const f = await register();
  for (const query of ['', 'keyword=&', 'keyword=%20%20&', `keyword=${'a'.repeat(51)}&`]) {
    const error = await f.api.get(`/api/questions/search?${query}page=1&size=10`, 400, 'VALIDATION_FAILED');
    assert.ok(error.fields.some(field => field.field === 'keyword'));
  }
});

scenario('PR-01/09/11/13：随机十题不重复、固定题单、保存进度、续练与结束', async () => {
  const f = await m1Fixture();
  const session = await startM1(f);
  assert.equal(session.questionCount, 10);
  assert.equal(new Set(session.questions.map(q => q.id)).size, 10);
  session.questions.forEach(noAnswers);
  assert.equal((await f.api.get('/api/practice/sessions/current')).id, session.id);
  assert.deepEqual((await f.api.get(`/api/practice/sessions/${session.id}`)).questions.map(q => q.id), session.questions.map(q => q.id));
  await f.api.put(`/api/practice/sessions/${session.id}/progress`, { currentIndex: 4 });
  assert.equal((await f.api.get(`/api/practice/sessions/${session.id}`)).currentIndex, 4);
  await answer(f, session, session.questions[0]);
  const finished = await f.api.post(`/api/practice/sessions/${session.id}/finish`, {});
  assert.equal(finished.answeredCount, 1); assert.equal(finished.correctCount, 1);
  assert.equal(await f.api.get('/api/practice/sessions/current'), null);
  assert.equal(items(await f.api.get('/api/practice/sessions')).length, 1);
});

scenario('PR-02/03/04：题不足 409、接受少抽和空池始终 409', async () => {
  const f = await m1Fixture('sparse');
  assert.equal((await startM1(f, {}, 409, 'POOL_INSUFFICIENT')).available, 3);
  assert.equal((await startM1(f, { acceptFewer: true })).questionCount, 3);
  const empty = await m1Fixture('empty');
  for (const acceptFewer of [false, true]) assert.equal((await startM1(empty, { acceptFewer }, 409, 'POOL_INSUFFICIENT')).available, 0);
});

scenario('PR-06/07/08/12/19/21：四种题型判分、重复提交保留首次结果、自动完成', async () => {
  const f = await m1Fixture('four');
  for (const pass of [true, false]) {
    const session = await startM1(f, { acceptFewer: true });
    for (const q of session.questions) {
      const first = await answer(f, session, q, pass ? correct(q) : incorrect(q));
      assert.equal(first.correct, pass, q.type); assert.equal(first.alreadySubmitted, false);
      const replay = await answer(f, session, q, pass ? incorrect(q) : correct(q));
      assert.equal(replay.correct, pass); assert.equal(replay.alreadySubmitted, true);
      assert.deepEqual(replay.userAnswer, first.userAnswer);
    }
    const stored = await f.api.get(`/api/practice/sessions/${session.id}`);
    assert.equal(stored.status, 'COMPLETED'); assert.equal(stored.answeredCount, 4);
    assert.equal(stored.correctCount, pass ? 4 : 0);
  }
  for (const userAnswer of [[], ['A', 'B', 'C']]) {
    const session = await startM1(f, { types: ['MULTIPLE'], acceptFewer: true });
    assert.equal((await answer(f, session, session.questions[0], userAnswer)).correct, false);
  }
});

scenario('CA-03 ST-03：章节按不同题计已做，重复提交不增加作答率分母', async () => {
  const f = await m1Fixture('four');
  const first = await startM1(f, { acceptFewer: true });
  const [q1, q2] = first.questions;
  await answer(f, first, q1); await answer(f, first, q1, incorrect(q1));
  await answer(f, first, q2, incorrect(q2));
  const again = await startM1(f, { acceptFewer: true }); await answer(f, again, q1);
  const tree = await f.api.get(`/api/categories?rootId=${f.root.id}`);
  const chapter = tree.children[0].children.find(n => n.id === f.chapter.id);
  assert.equal(chapter.doneCount, 2); approx(chapter.correctRate, 2 / 3);
});

scenario('PR-15/17：背题不能提交且不计进度，UNDONE 排除正式提交题', async () => {
  const f = await m1Fixture('four');
  const recite = await startM1(f, { reciteMode: true, acceptFewer: true });
  assert.ok(recite.questions.every(q => q.correctAnswer || q.acceptedAnswers));
  await f.api.post(`/api/practice/sessions/${recite.id}/answers`, { questionId: recite.questions[0].id, userAnswer: ['A'], timeSpentSec: 1 }, 409, 'CONFLICT');
  await f.api.post(`/api/practice/sessions/${recite.id}/finish`, {});
  assert.equal((await f.api.get(`/api/categories?rootId=${f.root.id}`)).doneCount, 0);
  const s = await startM1(f, { acceptFewer: true }); await answer(f, s, s.questions[0]);
  const undone = await startM1(f, { scope: 'UNDONE', acceptFewer: true });
  assert.equal(undone.questionCount, 3); assert.ok(undone.questions.every(q => q.id !== s.questions[0].id));
});

scenario('CA-07 DY-01/03：顺序复用、每日复用与未全答不能完成', async () => {
  const f = await m1Fixture('four');
  const seq = await startM1(f, { mode: 'SEQUENTIAL' });
  assert.equal(seq.questionCount, 4);
  assert.equal((await startM1(f, { mode: 'SEQUENTIAL' }, 200)).id, seq.id);
  await startM1(f, { mode: 'SEQUENTIAL', categoryIds: [f.root.id, f.chapter.id] }, 400, 'VALIDATION_FAILED');
  const daily = await startM1(f, { mode: 'DAILY' });
  assert.equal(daily.questionCount, 10);
  assert.equal((await startM1(f, { mode: 'DAILY' }, 200)).id, daily.id);
  await f.api.post(`/api/practice/sessions/${daily.id}/finish`, {});
  assert.equal((await f.api.get(`/api/practice/sessions/${daily.id}`)).completedAt, null);
  for (const q of daily.questions) await answer(f, daily, q);
  assert.ok((await f.api.get(`/api/practice/sessions/${daily.id}`)).completedAt);
});

scenario('WR-01/03/04/05/07：错误累加、错题连对三次移出、再错恢复、手动移出', async () => {
  const f = await m1Fixture('single'); const q = f.questions[0];
  const wrong = async () => items(await f.api.get('/api/wrong-questions?sort=MOST'));
  for (let n = 1; n <= 2; n++) {
    await answer(f, await startM1(f, { acceptFewer: true }), q, incorrect(q));
    assert.equal((await wrong())[0].wrongCount, n);
  }
  assert.equal((await f.api.get('/api/wrong-questions/groups'))[0].count, 1);
  for (let n = 1; n <= 3; n++) {
    const s = await startM1(f, { mode: 'WRONG', count: undefined }); await answer(f, s, q);
    const list = await wrong(); if (n < 3) assert.equal(list[0].correctStreak, n); else assert.equal(list.length, 0);
  }
  await answer(f, await startM1(f, { acceptFewer: true }), q, incorrect(q));
  assert.equal((await wrong())[0].wrongCount, 3); assert.equal((await wrong())[0].correctStreak, 0);
  await f.api.delete(`/api/wrong-questions/${q.id}`); assert.equal((await wrong()).length, 0);
});

scenario('PR-24 RETRY：原顺序、只取错题、无错题 409、他人来源 404、连对累加', async () => {
  const f = await m1Fixture('four'); const s = await startM1(f, { acceptFewer: true });
  for (const [i, q] of s.questions.entries()) await answer(f, s, q, i < 2 ? incorrect(q) : correct(q));
  const retry = await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromSessionId: s.id }, 201);
  assert.deepEqual(retry.questions.map(q => q.id), s.questions.slice(0, 2).map(q => q.id));
  for (const q of retry.questions) await answer(f, retry, q);
  assert.ok(items(await f.api.get('/api/wrong-questions')).every(q => q.correctStreak === 1));
  assert.equal((await f.api.post('/api/practice/sessions', { mode: 'RETRY', fromSessionId: retry.id }, 409, 'POOL_INSUFFICIENT')).available, 0);
  const stranger = await register();
  await stranger.api.post('/api/practice/sessions', { mode: 'RETRY', fromSessionId: s.id }, 404, 'NOT_FOUND');
});

scenario('WR-10/11/12：收藏/笔记更新、不传保留、空串清空、取消删除', async () => {
  const f = await m1Fixture('single'); const q = f.questions[0];
  assert.equal((await f.api.put(`/api/favorites/${q.id}`, { note: '初次笔记' })).note, '初次笔记');
  assert.equal((await f.api.put(`/api/favorites/${q.id}`, {})).note, '初次笔记');
  const cleared = (await f.api.put(`/api/favorites/${q.id}`, { note: '' })).note;
  assert.ok(cleared === null || cleared === '', '空串应清空笔记；接口未规定空值回传形式');
  await f.api.put(`/api/favorites/${q.id}`, { note: '字'.repeat(201) }, 400, 'VALIDATION_FAILED');
  assert.equal((await startM1(f, { mode: 'FAVORITE', count: undefined })).questionCount, 1);
  assert.equal(items(await f.api.get('/api/favorites')).length, 1);
  await f.api.delete(`/api/favorites/${q.id}`);
  assert.equal(items(await f.api.get('/api/favorites')).length, 0);
  assert.equal((await f.api.get(`/api/questions/${q.id}`)).note, null);
});

scenario('WR-13：阈值 1–10 校验，调小不立即移出，下次答对按新阈值判断', async () => {
  const f = await m1Fixture('single'); const q = f.questions[0];
  assert.equal((await f.api.get('/api/me')).wrongRemoveThreshold, 3);
  for (const wrongRemoveThreshold of [0, 11]) await f.api.put('/api/me/settings', { wrongRemoveThreshold }, 400, 'VALIDATION_FAILED');
  await answer(f, await startM1(f, { acceptFewer: true }), q, incorrect(q));
  for (let n = 0; n < 2; n++) await answer(f, await startM1(f, { mode: 'WRONG', count: undefined }), q);
  assert.equal((await f.api.put('/api/me/settings', { wrongRemoveThreshold: 2 })).wrongRemoveThreshold, 2);
  assert.equal(items(await f.api.get('/api/wrong-questions')).length, 1);
  await answer(f, await startM1(f, { mode: 'WRONG', count: undefined }), q);
  assert.equal(items(await f.api.get('/api/wrong-questions')).length, 0);
});

scenario('WR-13 QA-002：错题阈值拒绝小数且不改变已保存设置', async () => {
  const f = await register();
  const error = await f.api.put('/api/me/settings', { wrongRemoveThreshold: 1.5 }, 400, 'VALIDATION_FAILED');
  assert.ok(error.fields.some(field => field.field === 'wrongRemoveThreshold'));
  assert.equal((await f.api.get('/api/me')).wrongRemoveThreshold, 3);
});

scenario('WR-13 QA-003：错题阈值拒绝数字字符串', async () => {
  const f = await register();
  const error = await f.api.put('/api/me/settings', { wrongRemoveThreshold: '2' }, 400, 'VALIDATION_FAILED');
  assert.ok(error.fields.some(field => field.field === 'wrongRemoveThreshold'));
  assert.equal((await f.api.get('/api/me')).wrongRemoveThreshold, 3);
});

scenario('API-0.1：userAnswer 每一项必须是原生 JSON 字符串', async () => {
  const f = await m1Fixture('single'); const s = await startM1(f, { acceptFewer: true });
  const error = await f.api.post(`/api/practice/sessions/${s.id}/answers`, {
    questionId: f.questions[0].id, userAnswer: [1], timeSpentSec: 1,
  }, 400, 'VALIDATION_FAILED');
  assert.ok(error.fields.some(field => field.field === 'userAnswer[0]'));
  assert.equal((await f.api.get(`/api/practice/sessions/${s.id}`)).answeredCount, 0);
});

scenario('WR-09 RETEST-01：未知错题排序值返回 400', async () => {
  const f = await register();
  const error = await f.api.get('/api/wrong-questions?sort=UNKNOWN', 400, 'VALIDATION_FAILED');
  assert.ok(error.fields.some(field => field.field === 'sort'));
});

scenario('NF-01 AD-01：他人练习读写 404、普通用户后台 403', async () => {
  const f = await m1Fixture('single'); const s = await startM1(f, { acceptFewer: true });
  const other = await register();
  await other.api.get(`/api/practice/sessions/${s.id}`, 404, 'NOT_FOUND');
  await other.api.put(`/api/practice/sessions/${s.id}/progress`, { currentIndex: 0 }, 404, 'NOT_FOUND');
  await other.api.post(`/api/practice/sessions/${s.id}/answers`, { questionId: f.questions[0].id, userAnswer: ['A'], timeSpentSec: 1 }, 404, 'NOT_FOUND');
  await f.api.get('/api/admin/questions', 403, 'FORBIDDEN');
});

scenario('AD-17：纠错 201 返回对象，空内容和超长内容拒绝', async () => {
  const f = await m1Fixture('single'); const path = `/api/questions/${f.questions[0].id}/feedback`;
  for (const content of ['', '字'.repeat(501)]) await f.api.post(path, { type: 'OTHER', content }, 400, 'VALIDATION_FAILED');
  const feedback = await f.api.post(path, { type: 'ANALYSIS_UNCLEAR', content: '请补充说明' }, 201);
  assert.ok(feedback.id); assert.equal(feedback.questionId, f.questions[0].id); assert.equal(feedback.status, 'PENDING');
});

const deferred = process.env.QA_SCOPE === 'all' ? [] : process.env.QA_SCOPE === 'm1+m2' ? ['管理 CRUD/禁用/Excel（M3）'] : ['考试接口及考试来源 RETRY（M2 另跑）', '统计接口（M2 另跑）', '管理 CRUD/禁用/Excel（M3）'];
for (const area of deferred) {
  test(`本分组不覆盖，非失败：${area}`, { skip: '阶段范围；未作为业务通过或失败' }, () => {});
}
