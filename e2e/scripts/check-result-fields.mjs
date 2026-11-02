import { readFile } from 'node:fs/promises';

const path = process.argv[2];
if (!path) { console.error('用法：node scripts/check-result-fields.mjs <M2 walk-result.json>'); process.exit(2); }
const evidence = JSON.parse(await readFile(path, 'utf8'));
let failed = 0;
for (const key of ['manualResult', 'automaticResult']) {
  const result = evidence[key];
  if (!result?.items?.length) { console.error(`${key} 缺少实际响应，不能验证`); failed++; continue; }
  const missing = result.items.filter(item => !Number.isInteger(item.blankCount) || item.blankCount < 0 || !Object.hasOwn(item, 'imageUrl'))
    .map(item => ({ questionId: item.questionId, type: item.type, hasBlankCount: Object.hasOwn(item, 'blankCount'), hasImageUrl: Object.hasOwn(item, 'imageUrl') }));
  if (missing.length) failed++;
  console.log(JSON.stringify({ contract: 'API-6 成绩单 blankCount/imageUrl', result: key, recordId: result.recordId, pass: !missing.length, missing }));
}
process.exitCode = failed ? 1 : 0;
