import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';

export const BASE_URL = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const unique = (prefix = 'qa') => `${prefix}${randomBytes(6).toString('hex')}`;
export const password = () => `Qa_${randomBytes(12).toString('hex')}`;

export async function available(url = `${BASE_URL}/api/app/version?platform=android`) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(4000) });
    return response.status === 200;
  } catch {
    return false;
  }
}

export function serviceError(url = BASE_URL) {
  return `服务未启动或不可用：${url}。请先运行 npm run stack:up，或设置 BASE_URL 指向本地测试后端。`;
}

export class Api {
  constructor(token) { this.token = token; }

  async request(method, path, body, status = 200, code = 'OK') {
    const multipart = body instanceof FormData;
    const headers = {};
    if (this.token) headers.Authorization = `Bearer ${this.token}`;
    if (body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await fetch(`${BASE_URL}${path}`, {
        method, headers,
        body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
        signal: AbortSignal.timeout(12000),
      });
    } catch {
      throw new Error(`${serviceError()} 请求：${method} ${path}`);
    }
    const raw = await response.text();
    let payload;
    try { payload = JSON.parse(raw); }
    catch { assert.fail(`${method} ${path}: 返回非 JSON，HTTP ${response.status}`); }
    // Never dump auth responses or request bodies: both can contain credentials.
    assert.equal(response.status, status,
      `${method} ${path}: HTTP ${response.status}, code=${payload.code}, message=${payload.message}`);
    assert.equal(payload.code, code, `${method} ${path}: envelope code`);
    assert.equal(typeof payload.message, 'string');
    assert.ok(Object.hasOwn(payload, 'data'), `${method} ${path}: 缺少 data`);
    return payload.data;
  }

  get(path, status = 200, code = 'OK') { return this.request('GET', path, undefined, status, code); }
  post(path, body, status = 200, code = 'OK') { return this.request('POST', path, body, status, code); }
  put(path, body, status = 200, code = 'OK') { return this.request('PUT', path, body, status, code); }
  delete(path, status = 200, code = 'OK') { return this.request('DELETE', path, undefined, status, code); }
}

export async function register() {
  const credentials = { username: unique(), password: password() };
  const data = await new Api().post('/api/auth/register', credentials, 201);
  assert.equal(typeof data.token, 'string');
  assert.ok(Date.parse(data.expiresAt) > Date.now());
  return { api: new Api(data.token), user: data.user, credentials, auth: data };
}

let adminPromise;
export async function admin() {
  // These are disposable dev defaults explicitly provided by the task, not real secrets.
  adminPromise ??= new Api().post('/api/auth/login', {
    username: process.env.ADMIN_USERNAME || 'admin',
    password: process.env.ADMIN_PASSWORD || 'admin123',
  }).then(data => new Api(data.token));
  return adminPromise;
}

export function items(page) {
  assert.ok(Array.isArray(page.items), '分页缺少 items');
  assert.equal(typeof page.total, 'number');
  return page.items;
}

export function noAnswers(value) {
  for (const field of ['correctAnswer', 'acceptedAnswers', 'analysis', 'analysisHtml']) {
    assert.ok(!Object.hasOwn(value, field), `作答前泄露 ${field}`);
  }
}

export function approx(actual, expected) {
  assert.equal(typeof actual, 'number');
  assert.ok(Math.abs(actual - expected) < 0.011, `expected ${expected}, got ${actual}`);
}
