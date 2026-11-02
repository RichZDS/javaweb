import assert from 'node:assert/strict';
import ExcelJS from 'exceljs';
import { BASE_URL, items } from './http.mjs';

export const excelHeaders = ['分类路径', '题型', '题干', '选项A', '选项B', '选项C', '选项D', '选项E', '选项F', '答案', '解析', '难度', '标签', '来源'];
export async function excelFile(rows) {
  const workbook = new ExcelJS.Workbook(); const sheet = workbook.addWorksheet('题目');
  sheet.addRow(excelHeaders); for (const row of rows) sheet.addRow(row);
  const form = new FormData();
  form.set('file', new Blob([await workbook.xlsx.writeBuffer()], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), 'qa-original.xlsx');
  return form;
}

// For business restrictions whose exact 4xx is not specified in api.md. Never
// accept 500, and each caller also checks that the forbidden mutation had no effect.
export async function clientError(api, method, path, body) {
  const multipart = body instanceof FormData;
  const response = await fetch(`${BASE_URL}${path}`, {
    method, headers: { Authorization: `Bearer ${api.token}`, ...(body !== undefined && !multipart ? { 'Content-Type': 'application/json' } : {}) },
    ...(body === undefined ? {} : { body: multipart ? body : JSON.stringify(body) }), signal: AbortSignal.timeout(10000),
  });
  assert.ok(response.status >= 400 && response.status < 500, `${method} ${path}: 应拒绝该操作，实际 HTTP ${response.status}`);
  const payload = await response.json(); assert.notEqual(payload.code, 'OK');
  return payload;
}

export async function findItem(api, path, predicate) {
  for (let page = 1; ; page++) {
    const result = await api.get(`${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`);
    const rows = items(result); const found = rows.find(predicate); if (found) return found;
    if (!rows.length || page * result.size >= result.total) return null;
  }
}

export function paperBody(p, overrides = {}) {
  return {
    title: p.title, categoryId: p.categoryId, description: p.description, mode: p.mode,
    durationMin: p.durationMin, passScore: p.passScore,
    ...(p.mode === 'FIXED' ? { questions: p.questions.map(q => ({ questionId: q.questionId, score: q.score, sort: q.sort })) }
      : { rules: p.rules.map(r => ({ categoryId: r.categoryId, type: r.type, count: r.count, score: r.score })) }),
    ...overrides,
  };
}
