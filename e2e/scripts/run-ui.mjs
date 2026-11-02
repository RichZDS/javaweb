import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { available, BASE_URL } from '../lib/http.mjs';
import { APP_URL, ADMIN_URL } from '../lib/stack-urls.mjs';
import { trackChild } from '../lib/process-owner.mjs';

const endpoints = [
  ['后端', `${BASE_URL}/api/app/version?platform=android`],
  ['H5', APP_URL],
  ...(['m1', 'm2', 'pm', 'settings'].includes(process.env.QA_SCOPE) ? [] : [['管理后台', ADMIN_URL]]),
];
const missing = [];
for (const [label, url] of endpoints) if (!url || !await available(url)) missing.push(`${label} ${url || '本测试栈未启动'}`);
if (missing.length) {
  console.error(`服务未启动或不可用：${missing.join('；')}。请先 npm run stack:up。`);
  process.exitCode = 2;
} else {
  const child = spawn(process.execPath, ['./node_modules/@playwright/test/cli.js', 'test', ...process.argv.slice(2)], {
    cwd: fileURLToPath(new URL('..', import.meta.url)), stdio: 'inherit',
    env: { ...process.env, PLAYWRIGHT_BROWSERS_PATH: process.env.PLAYWRIGHT_BROWSERS_PATH || '/data/ms-playwright' },
  });
  const tracked = trackChild(child, fileURLToPath(new URL('../..', import.meta.url)), fileURLToPath(new URL('../artifacts/processes', import.meta.url)), 'ui-runner');
  tracked.ready.catch(error => { console.error(error.message); process.exitCode = 1; });
  child.on('exit', (code, signal) => { process.exitCode = signal ? 1 : code; });
  child.on('error', error => { console.error(error.message); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => tracked.stop(signal).catch(error => { console.error(error.message); process.exitCode = 1; }));
}
