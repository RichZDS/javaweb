import { readFileSync, realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const stateFile = fileURLToPath(new URL('../.stack-state.json', import.meta.url));
const ownerRoot = realpathSync(fileURLToPath(new URL('../../', import.meta.url)));
let state;
try { state = JSON.parse(readFileSync(stateFile, 'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw new Error('无法读取本工作树测试栈地址，请检查 .stack-state.json'); }
if (state && state.repo !== ownerRoot) throw new Error('测试栈状态不属于本工作树，拒绝读取其地址');

export const BASE_URL = (process.env.BASE_URL || state?.urls?.api || 'http://localhost:8080').replace(/\/$/, '');
export const APP_URL = process.env.APP_URL || state?.urls?.app || 'http://127.0.0.1:4173/quiz/';
export const ADMIN_URL = process.env.ADMIN_URL || (state ? state.urls?.admin : 'http://127.0.0.1:4174');
export const TEST_HOSTS = new Set([BASE_URL, APP_URL, ADMIN_URL].filter(Boolean).map(url => new URL(url).hostname));
export function allowTestHost(route) { return TEST_HOSTS.has(new URL(route.request().url()).hostname) ? route.continue() : route.abort(); }
