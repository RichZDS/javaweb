// Focused infrastructure proof using disposable fake build commands. These are NOT product tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, writeFile, readFile, cp, rm, access } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';

const scripts = fileURLToPath(new URL('.', import.meta.url));
const exists = p => access(p).then(() => true, () => false);
async function port() {
  const s = createServer();
  await new Promise(resolve => s.listen(0, '127.0.0.1', resolve));
  const value = s.address().port;
  await new Promise(resolve => s.close(resolve));
  return value;
}
async function exec(file, args, env) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [file, ...args], { env, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    child.stdout.on('data', data => { output += data; });
    child.stderr.on('data', data => { output += data; });
    child.on('error', reject);
    child.on('exit', code => resolve({ code, output }));
  });
}
async function sandbox(t) {
  const root = await mkdtemp(path.join(tmpdir(), 'javaweb-harness-'));
  const bin = path.join(root, 'bin');
  // Only our fake executables: no accidental host gates or toolchain during self-checks.
  const env = { ...process.env, PATH: bin, API_PORT: String(await port()), APP_PORT: String(await port()), ADMIN_PORT: String(await port()) };
  const stack = path.join(root, 'e2e/scripts/stack.mjs');
  for (const dir of ['bin', 'backend/target', 'app/dist/build/h5', 'admin/dist', 'e2e/scripts']) await mkdir(path.join(root, dir), { recursive: true });
  await cp(path.join(scripts, 'stack.mjs'), stack);
  await cp(path.join(scripts, 'static-server.mjs'), path.join(root, 'e2e/scripts/static-server.mjs'));
  for (const file of ['backend/pom.xml', 'backend/target/test.jar', 'app/package-lock.json', 'admin/package-lock.json']) await writeFile(path.join(root, file), '{}');
  for (const name of ['mvn', 'npm']) await writeFile(path.join(bin, name), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
  const fake = `#!${process.execPath}
const http = require('node:http');
const port = process.argv.find(a => a.startsWith('--server.port=')).split('=')[1];
const server = http.createServer((req,res) => { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify({code:'OK',message:'ok',data:{path:req.url}})); });
server.listen(Number(port), '127.0.0.1');
process.on('SIGTERM',()=>{server.closeAllConnections();server.close(()=>process.exit(0));});
`;
  await writeFile(path.join(bin, 'java'), fake, { mode: 0o755 });
  await writeFile(path.join(root, 'app/dist/build/h5/index.html'), '<html>H5 harness fixture</html>');
  await writeFile(path.join(root, 'admin/dist/index.html'), '<html>Admin harness fixture</html>');
  await writeFile(path.join(root, 'admin/dist/app.js'), 'globalThis.fixture = true;');
  t.after(async () => { await exec(stack, ['down'], env); await rm(root, { recursive: true, force: true }); });
  return { root, bin, stack, env, stateFile: path.join(root, 'e2e/.stack-state.json') };
}

for (const machineGate of [false, true]) {
  test(`栈自检：五个构建命令均通过 heavy${machineGate ? ' → sozai-lock' : ''}，运行服务不持锁`, { timeout: 30000 }, async t => {
    const f = await sandbox(t);
    const trace = path.join(f.root, 'gates.log');
    const fakeGate = name => `#!/bin/sh\necho '${name} '"$*" >> "$QA_GATE_TRACE"\n${name === 'sozai-lock' ? 'shift 2\n' : ''}exec "$@"\n`;
    await writeFile(path.join(f.bin, 'heavy'), fakeGate('heavy'), { mode: 0o755 });
    if (machineGate) await writeFile(path.join(f.bin, 'sozai-lock'), fakeGate('sozai-lock'), { mode: 0o755 });
    const result = await exec(f.stack, ['up'], { ...f.env, QA_GATE_TRACE: trace });
    assert.equal(result.code, 0, result.output);
    const builds = ['mvn -B -ntp -DskipTests package', 'npm ci --no-audit --no-fund', 'npm run build:h5', 'npm ci --no-audit --no-fund', 'npm run build'];
    const expected = builds.flatMap(command => machineGate ? [
      `heavy ${path.join(f.bin, 'sozai-lock')} heavy -- ${command}`, `sozai-lock heavy -- ${command}`,
    ] : [`heavy ${command}`]);
    assert.deepEqual((await readFile(trace, 'utf8')).trim().split('\n'), expected);
    assert.equal((await exec(f.stack, ['down'], f.env)).code, 0);
    assert.deepEqual((await readFile(trace, 'utf8')).trim().split('\n'), expected);
  });
}

test('栈自检：起服务、SPA/静态资源/API 代理、重复启动拒绝、完整关闭', { timeout: 30000 }, async t => {
  const f = await sandbox(t);
  const up = await exec(f.stack, ['up'], f.env);
  assert.equal(up.code, 0, up.output);
  const state = JSON.parse(await readFile(f.stateFile, 'utf8'));
  assert.equal(state.processes.length, 3);
  assert.ok(await exists(state.tempDir));
  const admin = `http://127.0.0.1:${f.env.ADMIN_PORT}`;
  assert.match(await (await fetch(`${admin}/history-route`)).text(), /Admin harness/);
  assert.match((await fetch(`${admin}/app.js`)).headers.get('content-type'), /javascript/);
  assert.equal((await fetch(`${admin}/missing.js`)).status, 404);
  assert.equal((await (await fetch(`${admin}/api/test?x=1`)).json()).data.path, '/api/test?x=1');
  assert.match(await (await fetch(`http://127.0.0.1:${f.env.APP_PORT}/quiz/history-route`)).text(), /H5 harness/);
  assert.notEqual((await exec(f.stack, ['up'], f.env)).code, 0);
  assert.equal((await exec(f.stack, ['down'], f.env)).code, 0);
  assert.equal(await exists(f.stateFile), false);
  assert.equal(await exists(state.tempDir), false);
  for (const p of state.ports) {
    const probe = createServer();
    await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(p, '127.0.0.1', resolve); });
    await new Promise(resolve => probe.close(resolve));
  }
});

for (const scope of ['m1', 'm2']) test(`栈自检：${scope} 无管理后台也可启动，隔离追加种子并关闭两个进程`, { timeout: 30000 }, async t => {
  const f = await sandbox(t);
  await rm(path.join(f.root, 'admin'), { recursive: true });
  await mkdir(path.join(f.root, 'backend/src/main/resources/seed'), { recursive: true });
  await writeFile(path.join(f.root, 'backend/src/main/resources/seed/00-original.json'), '{}');
  await mkdir(path.join(f.root, 'e2e/lib'));
  for (const file of ['m1-fixtures.mjs', 'm2-fixtures.mjs', 'http.mjs']) await cp(path.join(scripts, '../lib', file), path.join(f.root, 'e2e/lib', file));
  const result = await exec(f.stack, ['up'], { ...f.env, QA_SCOPE: scope });
  assert.equal(result.code, 0, result.output);
  assert.doesNotMatch(result.output, /ADMIN_URL/);
  const state = JSON.parse(await readFile(f.stateFile, 'utf8'));
  assert.equal(state.processes.length, 2);
  assert.ok(await exists(path.join(state.tempDir, 'seed/00-original.json')));
  const seed = JSON.parse(await readFile(path.join(state.tempDir, 'seed/99-qa-m1.json')));
  assert.equal(seed.questions.length, 20);
  if (scope === 'm2') {
    assert.equal(seed.papers.length, 3);
    assert.equal(seed.papers.find(p => p.durationMin === 1).questions.length, 4);
    const refs = new Set(seed.questions.map(q => q.ref));
    assert.ok(seed.papers.filter(p => p.mode === 'FIXED').every(p => p.questions.every(q => refs.has(q.ref))));
  }
  await exec(f.stack, ['down'], f.env);
  assert.equal(await exists(state.tempDir), false);
});

test('栈自检：构建失败保留日志，清除状态和临时目录', { timeout: 10000 }, async t => {
  const f = await sandbox(t);
  await writeFile(path.join(f.bin, 'mvn'), '#!/bin/sh\necho intentional-build-failure\nexit 23\n', { mode: 0o755 });
  const result = await exec(f.stack, ['up'], f.env);
  assert.equal(result.code, 1);
  assert.match(result.output, /退出码 23/);
  assert.equal(await exists(f.stateFile), false);
  assert.match(await readFile(path.join(f.root, 'e2e/artifacts/stack/backend-build.log'), 'utf8'), /intentional-build-failure/);
});

test('栈自检：占用端口拒绝启动，不影响原服务', { timeout: 10000 }, async t => {
  const f = await sandbox(t);
  const occupied = createServer();
  await new Promise(resolve => occupied.listen(Number(f.env.API_PORT), '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => occupied.close(resolve)));
  const result = await exec(f.stack, ['up'], f.env);
  assert.equal(result.code, 1);
  assert.match(result.output, /已占用/);
  assert.equal(await exists(f.stateFile), false);
  assert.ok(occupied.listening);
});

test('栈自检：构建中断后停止子进程并清除状态和临时目录', { timeout: 10000 }, async t => {
  const f = await sandbox(t);
  await writeFile(path.join(f.bin, 'mvn'), `#!${process.execPath}\nconsole.log('building-pid=' + process.pid);\nsetInterval(() => {}, 1000);\n`, { mode: 0o755 });
  const child = spawn(process.execPath, [f.stack, 'up'], { env: f.env, stdio: 'ignore' });
  const ended = new Promise(resolve => child.once('exit', (code, signal) => resolve({ code, signal })));
  t.after(() => { if (child.exitCode === null) child.kill('SIGTERM'); });
  let state;
  let pid;
  for (let i = 0; i < 80; i++) {
    const log = await readFile(path.join(f.root, 'e2e/artifacts/stack/backend-build.log'), 'utf8').catch(() => '');
    const match = log.match(/building-pid=(\d+)/);
    if (match) {
      pid = Number(match[1]);
      state = JSON.parse(await readFile(f.stateFile, 'utf8'));
      break;
    }
    await delay(50);
  }
  assert.ok(pid, 'fake Maven 已进入构建');
  child.kill('SIGTERM');
  assert.equal((await ended).code, 143);
  assert.equal(await exists(f.stateFile), false);
  assert.equal(await exists(state.tempDir), false);
  assert.throws(() => process.kill(pid, 0), /ESRCH/);
});
