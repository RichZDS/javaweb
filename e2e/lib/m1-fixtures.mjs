import assert from 'node:assert/strict';
import { register, items } from './http.mjs';

export const rootName = 'QA M1 自动化验收';
export const pools = { mixed: 12, four: 4, single: 1, sparse: 3, empty: 0 };
export function m1Seed() {
  const questions = [];
  for (const [pool, count] of Object.entries(pools)) {
    for (let i = 0; i < count; i++) {
      const type = ['single', 'sparse'].includes(pool) ? 'SINGLE' : ['SINGLE', 'MULTIPLE', 'JUDGE', 'FILL'][i % 4];
      questions.push({
        category: `qa-m1-${pool}`, type, stem: `QA ${pool} 原创校验题 ${i + 1}：按题型选择指定的甲、甲和丙、正确，或填写 int 与 5。`,
        ...(type === 'SINGLE' || type === 'MULTIPLE' ? { options: ['甲', '乙', '丙'] } : {}),
        answer: type === 'SINGLE' ? ['A'] : type === 'MULTIPLE' ? ['A', 'C'] : type === 'JUDGE' ? ['T'] : [['int', 'Integer'], ['5']],
        ...(type === 'FILL' ? { fillOptions: { ignoreCase: true, ignoreSpaces: true } } : {}),
        analysis: '此题用于隔离验收：单选甲，多选甲和丙，判断正确，填空接受 int/Integer 和 5。',
        source: '原创自动化验收夹具', difficulty: 2, tags: ['qa-m1'],
      });
    }
  }
  return {
    categories: [{ key: 'qa-m1', name: rootName, children: [{ key: 'qa-m1-subject', name: '验收科目', children:
      Object.keys(pools).map(pool => ({ key: `qa-m1-${pool}`, name: `QA ${pool}` })),
    }] }], questions,
  };
}

export async function m1Fixture(pool = 'mixed') {
  const f = await register();
  const root = (await f.api.get('/api/categories')).find(n => n.name === rootName);
  assert.ok(root, '缺少 M1 夹具种子，请 QA_SCOPE=m1 npm run stack:up');
  const tree = await f.api.get(`/api/categories?rootId=${root.id}`);
  const subject = tree.children[0];
  const chapter = subject.children.find(n => n.name === `QA ${pool}`);
  assert.ok(chapter, `缺少章节 QA ${pool}`);
  await f.api.put('/api/me', { targetCategoryId: root.id });
  const found = await f.api.get(`/api/questions/search?keyword=QA%20${pool}&categoryId=${chapter.id}&page=1&size=100`);
  assert.equal(found.total, pools[pool]);
  const questions = [];
  for (const q of items(found)) questions.push(await f.api.get(`/api/questions/${q.id}`));
  return { ...f, root, subject, chapter, questions };
}

export function startM1(f, overrides = {}, status = 201, code = 'OK') {
  return f.api.post('/api/practice/sessions', {
    mode: 'RANDOM', categoryIds: [f.chapter.id], count: 10, scope: 'ALL',
    reciteMode: false, acceptFewer: false, ...overrides,
  }, status, code);
}
