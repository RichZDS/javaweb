import { readFile, writeFile, stat } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { trackChild } from '../lib/process-owner.mjs';

// Explicitly authorized application endpoints only; this script never manages the server.
const e2e = fileURLToPath(new URL('..', import.meta.url));
const stateFile = fileURLToPath(new URL('../.staging-state.json', import.meta.url));
for (const name of ['http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY', 'all_proxy', 'ALL_PROXY']) delete process.env[name];
Object.assign(process.env, { NO_PROXY: '*', no_proxy: '*', QA_STAGING: '1', QA_SHARED: '1',
  BASE_URL: 'https://43.142.166.108/quiz', APP_URL: 'https://43.142.166.108/quiz/', ADMIN_URL: 'https://43.142.166.108/quiz/admin/' });

async function credentials() {
  const file = process.env.STAGING_CREDENTIAL_FILE || '/data/crew/qzd-5b1e/board/staging-admin.txt';
  if (((await stat(file)).mode & 0o777) !== 0o600) throw new Error('管理员凭证文件权限必须是0600');
  const raw = await readFile(file, 'utf8');
  let values = {};
  try { values = JSON.parse(raw); } catch { /* Never print parser exceptions or raw contents. */ }
  const aliases = { username: 'username', admin_username: 'username', 用户名: 'username', 账号: 'username', password: 'password', admin_password: 'password', 密码: 'password' };
  const parsed = {};
  for (const [key, value] of Object.entries(values)) if (aliases[key.toLowerCase()]) parsed[aliases[key.toLowerCase()]] = value;
  for (const line of raw.split(/\r?\n/)) {
    const match = line.match(/^\s*(?:[-*]\s*)?(?:export\s+)?(ADMIN_USERNAME|ADMIN_PASSWORD|username|password|用户名|账号|密码)\s*[:=：]\s*(.*?)\s*$/i);
    if (match) {
      let value = match[2];
      if (value.length > 1 && (value[0] === '"' && value.at(-1) === '"' || value[0] === "'" && value.at(-1) === "'")) value = value.slice(1, -1);
      parsed[aliases[match[1].toLowerCase()]] = value;
    }
  }
  if (typeof parsed.username !== 'string' || !parsed.username || typeof parsed.password !== 'string' || !parsed.password) throw new Error('凭证文件格式无法识别；需要username/password键，内容未输出');
  process.env.ADMIN_USERNAME = parsed.username; process.env.ADMIN_PASSWORD = parsed.password;
}

try {
  const mode = process.argv[2];
  if (!['prepare', 'api', 'shuffle-api', 'app', 'exam', 'shuffle-ui', 'repro-submit', 'concurrency-api', 'admin-shuffle-ui', 'upload'].includes(mode)) throw new Error('用法：staging.mjs prepare|api|shuffle-api|app|exam|shuffle-ui|repro-submit|concurrency-api|admin-shuffle-ui|upload');
  if (['prepare', 'api', 'shuffle-api', 'shuffle-ui', 'admin-shuffle-ui'].includes(mode)) await credentials();
  else { delete process.env.ADMIN_USERNAME; delete process.env.ADMIN_PASSWORD; }
  let state;
  if (mode === 'prepare') {
    state = { fixtureRoot: `QA staging ${randomBytes(5).toString('hex')}`, startedAt: new Date().toISOString(), endpoint: process.env.BASE_URL };
    process.env.QA_FIXTURE_ROOT = state.fixtureRoot;
    const { admin } = await import('../lib/http.mjs'); const { m2Seed } = await import('../lib/m2-fixtures.mjs');
    const staff = await admin(); const seed = m2Seed(); const categories = new Map(); const questions = new Map();
    async function createCategories(nodes, parentId = null) {
      for (const node of nodes) {
        const saved = await staff.post('/api/admin/categories', { parentId, name: node.name, sort: 0 }, 201);
        categories.set(node.key, saved.id); if (node.children) await createCategories(node.children, saved.id);
      }
    }
    await createCategories(seed.categories);
    for (const q of seed.questions) {
      const saved = await staff.post('/api/admin/questions', { categoryId: categories.get(q.category), type: q.type, stem: q.stem,
        options: q.options?.map((text, i) => ({ key: 'ABCDEF'[i], text })),
        ...(q.type === 'FILL' ? { acceptedAnswers: q.answer, fillOptions: q.fillOptions } : { correctAnswer: q.answer }),
        analysis: q.analysis, difficulty: q.difficulty, tags: q.tags, source: q.source, status: 'PUBLISHED' }, 201);
      questions.set(q.ref, saved.id);
    }
    for (const p of seed.papers) {
      const saved = await staff.post('/api/admin/papers', { title: p.title, categoryId: categories.get(p.category), mode: p.mode,
        durationMin: p.durationMin, passScore: p.passScore, description: p.description,
        ...(p.mode === 'FIXED' ? { questions: p.questions.map((q, i) => ({ questionId: questions.get(q.ref), score: q.score, sort: i })) }
          : { rules: p.rules.map(r => ({ categoryId: categories.get(r.category), type: r.type, count: r.count, score: r.score })) }) }, 201);
      await staff.put(`/api/admin/papers/${saved.id}/status`, { status: 'ONLINE' });
    }
    await writeFile(stateFile, JSON.stringify(state, null, 2), { mode: 0o600 });
    console.log(JSON.stringify({ prepared: true, fixtureRoot: state.fixtureRoot, categories: categories.size, questions: questions.size, papers: seed.papers.length }));
  } else {
    state = JSON.parse(await readFile(stateFile, 'utf8')); process.env.QA_FIXTURE_ROOT = state.fixtureRoot;
    const api = ['api', 'shuffle-api', 'concurrency-api'].includes(mode);
    process.env.QA_SCOPE = ({ api: 'all', 'shuffle-api': 'shuffle', 'concurrency-api': 'concurrency', 'admin-shuffle-ui': 'admin-shuffle', app: 'm1', exam: 'm2', 'shuffle-ui': 'shuffle', 'repro-submit': 'm2', upload: 'm1' })[mode];
    process.env.QA_STAGE = `staging-${mode}`; process.env.QA_MAIN_COMMIT = process.env.STAGING_COMMIT || state.serverCommit || 'staging (runtime release not exposed)';
    const child = spawn(process.execPath, [mode === 'repro-submit' ? 'scripts/repro-concurrent-submit.mjs' : mode === 'upload' ? 'scripts/probe-upload.mjs' : api ? 'scripts/run-api.mjs' : 'scripts/run-ui.mjs'], { cwd: e2e, env: process.env, stdio: 'inherit' });
    const tracked = trackChild(child, fileURLToPath(new URL('../..', import.meta.url)), fileURLToPath(new URL('../artifacts/processes', import.meta.url)), `staging-${mode}`);
    tracked.ready.catch(error => { console.error(error.message); process.exitCode = 1; });
    child.on('exit', (code, signal) => { process.exitCode = signal ? 1 : code; });
    child.on('error', () => { console.error('预发布测试进程启动失败'); process.exitCode = 1; });
    for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => tracked.stop(signal).catch(error => { console.error(error.message); process.exitCode = 1; }));
  }
} catch (error) { console.error(error.message); process.exitCode = 1; }
