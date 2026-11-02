import { spawn } from 'node:child_process';
import { openSync, closeSync, constants } from 'node:fs';
import { readFile, writeFile, rename, mkdir, mkdtemp, rm, readdir, access, cp, realpath } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { setTimeout as delay } from 'node:timers/promises';
import { createServer } from 'node:net';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { checkOwned, stopOwned, trackChild } from '../lib/process-owner.mjs';

const e2e = fileURLToPath(new URL('..', import.meta.url));
const repo = await realpath(path.dirname(e2e));
const statePath = path.join(e2e, '.stack-state.json');
const logDir = path.join(e2e, 'artifacts', 'stack');
const m2 = ['m2', 'all', 'm3'].includes(process.env.QA_SCOPE);
const m1 = ['m1', 'm2'].includes(process.env.QA_SCOPE); // M1/M2 run without an admin frontend.
let state;
let buildChild;
let stopping = false;
async function exists(file) { return access(file).then(() => true, () => false); }
async function executable(name, cwd, env) {
  for (const directory of (env.PATH || '').split(path.delimiter)) {
    const candidate = path.resolve(cwd, directory, name);
    if (await access(candidate, constants.X_OK).then(() => true, () => false)) return candidate;
  }
  return null;
}
async function alive(proc) { return await checkOwned(proc, repo); }
async function persist() {
  await writeFile(`${statePath}.tmp`, JSON.stringify(state, null, 2), { mode: 0o600 });
  await rename(`${statePath}.tmp`, statePath);
}
async function stop() {
  if (!state) {
    if (!await exists(statePath)) { console.log('本测试栈未启动'); return; }
    state = JSON.parse(await readFile(statePath, 'utf8'));
  }
  if (state.repo !== repo) throw new Error('拒绝清理：状态文件缺少本工作树归属，保留状态及临时目录');
  await stopOwned(state.processes, repo, path.join(logDir, 'stop-processes.json'));
  const marker = path.join(state.tempDir, '.javaweb-e2e-owner');
  if (await readFile(marker, 'utf8').catch(() => '') === state.id) await rm(state.tempDir, { recursive: true, force: true });
  await rm(statePath, { force: true });
  console.log('本测试栈进程和临时 H2 目录已清理，构建与服务日志保留在 e2e/artifacts/stack/');
}
async function run(command, args, cwd, label, env = process.env) {
  // Gate each build so direct stack.mjs callers are covered too. Keep the shared
  // lock order heavy -> sozai-lock; stack.sh must not hold either lock outside us.
  let invocation = [command, ...args];
  const machineGate = await executable('sozai-lock', cwd, env);
  const crewGate = await executable('heavy', cwd, env);
  if (machineGate) invocation = [machineGate, 'heavy', '--', ...invocation];
  if (crewGate) invocation = [crewGate, ...invocation];
  const log = openSync(path.join(logDir, `${label}.log`), 'w', 0o600);
  console.log(`构建 ${label}（日志 e2e/artifacts/stack/${label}.log）`);
  try {
    const code = await new Promise((resolve, reject) => {
      const child = spawn(invocation[0], invocation.slice(1), { cwd, env, detached: true, stdio: ['ignore', log, log] });
      buildChild = trackChild(child, repo, path.join(logDir, 'processes'), label);
      buildChild.ready.catch(reject);
      child.once('error', reject);
      child.once('exit', (code, signal) => resolve(signal ? 1 : code));
    });
    if (code !== 0) throw new Error(`${label} 失败（退出码 ${code}），请查看对应构建日志`);
  } finally { buildChild = undefined; closeSync(log); }
}
async function start(command, args, label, cwd = repo) {
  const log = openSync(path.join(logDir, `${label}.log`), 'w', 0o600);
  const child = spawn(command, args, { cwd, detached: true, stdio: ['ignore', log, log] });
  const tracked = trackChild(child, repo, path.join(logDir, 'processes'), label);
  closeSync(log);
  const record = await tracked.ready;
  if (!record) throw new Error(`${label} 在登记进程前已退出`);
  child.unref();
  state.processes.push(record);
  await persist();
}
async function freePort(port = 0) {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.once('error', () => reject(new Error(`端口 ${port} 已占用；不会停止其他服务，请改用空闲端口`)));
    server.listen(port, '127.0.0.1', () => { const assigned = server.address().port; server.close(() => resolve(assigned)); });
  });
}
async function ready(url, label) {
  for (let i = 0; i < 120; i++) {
    for (const proc of state.processes) if (!await alive(proc)) throw new Error(`${proc.label} 提前退出；检查服务日志`);
    try { if ((await fetch(url, { signal: AbortSignal.timeout(1500) })).status === 200) {
      for (const proc of state.processes) if (!await alive(proc)) throw new Error(`${proc.label} 已退出`);
      return;
    } } catch {}
    await delay(1000);
  }
  throw new Error(`${label} 在 120 次探测后仍未就绪`);
}

async function up() {
  if (await exists(statePath)) throw new Error('已有测试栈状态，请先 stack.sh status 或 stack.sh down');
  for (const file of ['backend/pom.xml', 'app/package-lock.json', ...(m1 ? [] : ['admin/package-lock.json'])]) {
    if (!await exists(path.join(repo, file))) throw new Error(`缺少 ${file}：对应实现尚未合入，请收到工头通知后 git merge main`);
  }
  const requested = [process.env.API_PORT || '0', process.env.APP_PORT || '0', ...(m1 ? [] : [process.env.ADMIN_PORT || '0'])].map(Number);
  if (requested.some(p => !Number.isInteger(p) || p !== 0 && (p < 1024 || p > 65535))) throw new Error('端口为0自动分配，或指定1024–65535');
  const ports = [];
  for (const port of requested) {
    let assigned = await freePort(port);
    while (!port && ports.includes(assigned)) assigned = await freePort();
    if (ports.includes(assigned)) throw new Error('端口不能重复');
    ports.push(assigned);
  }
  await mkdir(logDir, { recursive: true });
  const tempDir = await mkdtemp(path.join(tmpdir(), 'javaweb-e2e-'));
  state = { id: randomUUID(), repo, tempDir, ports, urls: { api: `http://127.0.0.1:${ports[0]}`, app: `http://127.0.0.1:${ports[1]}/quiz/`, ...(!m1 ? { admin: `http://127.0.0.1:${ports[2]}` } : {}) }, processes: [], startedAt: new Date().toISOString() };
  await writeFile(path.join(tempDir, '.javaweb-e2e-owner'), state.id, { mode: 0o600 });
  await persist();
  try {
    await run('mvn', ['-B', '-ntp', '-DskipTests', 'package'], path.join(repo, 'backend'), 'backend-build');
    for (const frontend of m1 ? ['app'] : ['app', 'admin']) {
      await run('npm', ['ci', '--no-audit', '--no-fund'], path.join(repo, frontend), `${frontend}-install`);
      await run('npm', ['run', frontend === 'app' ? 'build:h5' : 'build'], path.join(repo, frontend), `${frontend}-build`, {
        ...process.env, VITE_API_BASE: '/api', ...(frontend === 'admin' ? { VITE_BASE: '/' } : {}),
      });
    }
    const jars = (await readdir(path.join(repo, 'backend', 'target'))).filter(f => f.endsWith('.jar') && !f.endsWith('-sources.jar'));
    if (jars.length !== 1) throw new Error(`预期一个后端 jar，实际 ${jars.length} 个`);
    const apiUrl = `http://127.0.0.1:${ports[0]}`;
    const seedArgs = [];
    if (m1 || m2) {
      const seedDir = path.join(tempDir, 'seed');
      await cp(path.join(repo, 'backend/src/main/resources/seed'), seedDir, { recursive: true });
      const seed = m2 ? (await import('../lib/m2-fixtures.mjs')).m2Seed() : (await import('../lib/m1-fixtures.mjs')).m1Seed();
      await writeFile(path.join(seedDir, '99-qa-m1.json'), JSON.stringify(seed));
      seedArgs.push(`--app.seed.location=file:${seedDir}/`);
    }
    await start('java', ['-Xmx512m', '-jar', path.join(repo, 'backend', 'target', jars[0]),
      '--spring.profiles.active=dev', `--server.port=${ports[0]}`, '--server.address=127.0.0.1',
      `--spring.datasource.url=jdbc:h2:file:${path.join(tempDir, 'db')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`,
      `--app.upload.dir=${path.join(tempDir, 'uploads')}`, ...seedArgs,
    ], 'backend', path.join(repo, 'backend'));
    await ready(`${apiUrl}/api/app/version?platform=android`, '后端');
    await start(process.execPath, [path.join(e2e, 'scripts', 'static-server.mjs'), path.join(repo, 'app', 'dist', 'build', 'h5'), String(ports[1]), apiUrl, '/quiz'], 'app');
    if (!m1) await start(process.execPath, [path.join(e2e, 'scripts', 'static-server.mjs'), path.join(repo, 'admin', 'dist'), String(ports[2]), apiUrl], 'admin');
    await ready(`http://127.0.0.1:${ports[1]}`, 'H5');
    if (!m1) await ready(`http://127.0.0.1:${ports[2]}`, '管理后台');
    console.log(`本地测试栈就绪\nBASE_URL=${apiUrl}\nAPP_URL=http://127.0.0.1:${ports[1]}/quiz/${m1 ? '' : `\nADMIN_URL=http://127.0.0.1:${ports[2]}`}`);
  } catch (error) { if (!stopping) await stop(); throw error; }
}

for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => {
  if (stopping) return;
  stopping = true;
  try { if (buildChild) await buildChild.stop(); await stop(); process.exit(signal === 'SIGINT' ? 130 : 143); }
  catch (error) { console.error(error.message); process.exit(1); }
});

try {
  if (process.argv[2] === 'up') await up();
  else if (process.argv[2] === 'down') await stop();
  else if (process.argv[2] === 'status') {
    if (!await exists(statePath)) console.log('本测试栈未启动');
    else {
      state = JSON.parse(await readFile(statePath, 'utf8'));
      for (const proc of state.processes) console.log(`${proc.label}: ${await alive(proc) ? 'running' : 'stopped'} pid=${proc.pid}`);
    }
  } else throw new Error('用法：stack.mjs up|down|status');
} catch (error) { console.error(error.message); process.exitCode = 1; }
