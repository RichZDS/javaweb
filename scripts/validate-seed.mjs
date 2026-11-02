#!/usr/bin/env node
// No dependencies. An optional directory argument supports isolated malformed fixtures.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultDirectory = fileURLToPath(new URL('../backend/src/main/resources/seed/', import.meta.url));
const directory = path.resolve(process.argv[2] ?? defaultDirectory);
const errors = [];
const warnings = [];
const fail = (at, message) => errors.push(`${at}: ${message}`);
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const nonempty = value => typeof value === 'string' && value.trim().length > 0;
const keyPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const types = ['SINGLE', 'MULTIPLE', 'JUDGE', 'FILL'];
const countBy = (items, getKey) => Object.fromEntries([...new Set(items.map(getKey))].sort().map(key => [key, items.filter(item => getKey(item) === key).length]));
const documents = [];
try {
  const files = fs.readdirSync(directory).filter(file => file.endsWith('.json')).sort();
  if (!files.length) fail(directory, '没有 JSON 文件');
  for (const file of files) {
    try {
      const data = JSON.parse(fs.readFileSync(path.join(directory, file), 'utf8'));
      if (!object(data)) { fail(file, '顶层必须为对象'); continue; }
      if (!['categories', 'questions', 'papers'].some(section => section in data)) fail(file, '缺少 categories/questions/papers');
      for (const section of Object.keys(data)) {
        if (!['categories', 'questions', 'papers'].includes(section)) fail(file, `未知部分 ${section}`);
        else if (!Array.isArray(data[section])) fail(file, `${section} 必须为数组`);
      }
      documents.push({ file, data });
    } catch (error) { fail(file, `不能读取合法 JSON：${error.message}`); }
  }
} catch (error) { fail(directory, `不能读取题库目录：${error.message}`); }

const categories = new Map();
const roots = [];
function readCategory(category, parent, level, file, at) {
  if (!object(category)) { fail(at, '分类必须为对象'); return; }
  if (!nonempty(category.key) || !keyPattern.test(category.key)) fail(at, '分类 key 只能由小写字母、数字和连字符组成');
  if (!nonempty(category.name)) fail(at, '分类名称为空');
  if (level > 3) fail(at, '分类不能超过三级');
  if (categories.has(category.key)) fail(at, `分类 key 重复：${category.key}`);
  else if (nonempty(category.key)) categories.set(category.key, { ...category, parent, level, file, at });
  if (level === 1 && nonempty(category.key)) roots.push(category.key);
  if (category.children !== undefined && !Array.isArray(category.children)) fail(at, 'children 必须为数组');
  const children = Array.isArray(category.children) ? category.children : [];
  if (level === 1 && !children.length) fail(at, '考试下必须有科目');
  if (level === 2 && (children.length < 3 || children.length > 5)) fail(at, '每科必须有 3–5 个章节');
  children.forEach((child, index) => readCategory(child, category.key, level + 1, file, `${at}.children[${index}]`));
}
for (const { file, data } of documents) {
  if (Array.isArray(data.categories)) data.categories.forEach((category, index) => readCategory(category, null, 1, file, `${file}.categories[${index}]`));
}
function within(categoryKey, ancestor) {
  const seen = new Set();
  while (categoryKey && !seen.has(categoryKey)) {
    if (categoryKey === ancestor) return true;
    seen.add(categoryKey);
    categoryKey = categories.get(categoryKey)?.parent;
  }
  return false;
}
function referenceCategory(key, file, at) {
  const category = categories.get(key);
  if (!category) fail(at, `分类不存在：${String(key)}`);
  else if (category.file > file) fail(at, `分类 ${key} 的定义文件必须排在引用之前`);
  return category;
}
const questions = [];
const references = new Map();
const stems = new Set();
for (const { file, data } of documents) {
  if (!Array.isArray(data.questions)) continue;
  data.questions.forEach((question, index) => {
    const at = `${file}.questions[${index}]`;
    if (!object(question)) { fail(at, '题目必须为对象'); return; }
    const q = { ...question, file, at };
    questions.push(q);
    // A heuristic for implausible distractors, not a correctness rule: a genuine
    // security question may legitimately discuss passwords and still be valid.
    if (Array.isArray(q.options)) q.options.forEach((option, optionIndex) => {
      if (typeof option !== 'string') return;
      const suspicious = option.match(/密码|所有用户|自动增加/g);
      if (suspicious) warnings.push(`${at} (${q.ref ?? '无 ref'}) 选项 ${String.fromCharCode(65 + optionIndex)} 命中“${[...new Set(suspicious)].join('、')}”，请人工复核干扰项是否相关`);
    });
    if (q.ref !== undefined) {
      if (!nonempty(q.ref) || !keyPattern.test(q.ref)) fail(at, 'ref 格式非法');
      else if (references.has(q.ref) || categories.has(q.ref)) fail(at, `ref 重复或与分类 key 冲突：${q.ref}`);
      else references.set(q.ref, q);
    }
    const category = referenceCategory(q.category, file, at);
    if (category && category.level !== 3) fail(at, '题只能挂三级章节');
    if (!types.includes(q.type)) fail(at, `题型非法：${q.type}`);
    if (!nonempty(q.stem)) fail(at, '题干为空');
    else {
      const normalized = q.stem.replace(/\s+/g, ' ').trim();
      if (stems.has(normalized)) fail(at, '题干重复');
      stems.add(normalized);
    }
    if (!nonempty(q.analysis)) fail(at, '解析为空');
    if (/占位|待替换/.test(`${q.stem} ${q.analysis}`)) fail(at, '有未完成占位内容');
    if (!nonempty(q.source) || !q.source.includes('原创')) fail(at, '每题须注明原创来源');
    if (!Number.isInteger(q.difficulty) || q.difficulty < 1 || q.difficulty > 5) fail(at, '难度必须为 1–5 的整数');
    if (!Array.isArray(q.tags) || q.tags.length === 0 || q.tags.some(tag => !nonempty(tag))) fail(at, 'tags 必须为非空字符串数组');
    if (!Array.isArray(q.answer) || !q.answer.length) { fail(at, 'answer 必须为非空数组'); return; }
    if (q.type === 'SINGLE' || q.type === 'MULTIPLE') {
      if (!Array.isArray(q.options) || q.options.length < 2 || q.options.length > 6 || q.options.some(option => !nonempty(option))) fail(at, '选择题选项必须为 2–6 个非空字符串');
      if (Array.isArray(q.options) && new Set(q.options).size !== q.options.length) fail(at, '选项内容重复');
      if (q.type === 'SINGLE' && q.answer.length !== 1) fail(at, '单选必须恰好一个答案');
      if (q.type === 'MULTIPLE' && q.answer.length < 2) fail(at, '多选至少两个答案');
      const keys = Array.isArray(q.options) ? q.options.map((_, i) => String.fromCharCode(65 + i)) : [];
      if (q.answer.some(answer => typeof answer !== 'string' || !keys.includes(answer))) fail(at, '答案必须引用已有选项 key');
      if (new Set(q.answer).size !== q.answer.length) fail(at, '答案有重复 key');
      if (q.fillOptions !== undefined) fail(at, '选择题不应包含 fillOptions');
    } else if (q.type === 'JUDGE') {
      if (q.options !== undefined) fail(at, '判断题不写 options');
      if (q.answer.length !== 1 || !['T', 'F'].includes(q.answer[0])) fail(at, '判断答案只能是 ["T"] 或 ["F"]');
      if (q.fillOptions !== undefined) fail(at, '判断题不应包含 fillOptions');
    } else if (q.type === 'FILL') {
      if (q.options !== undefined) fail(at, '填空题不写 options');
      if (q.answer.some(blank => !Array.isArray(blank) || blank.length === 0 || blank.some(answer => !nonempty(answer)))) fail(at, '每空必须是含非空可接受答案的数组');
      if (q.fillOptions !== undefined && (!object(q.fillOptions) || typeof q.fillOptions.ignoreCase !== 'boolean' || typeof q.fillOptions.ignoreSpaces !== 'boolean')) fail(at, 'fillOptions 的两个开关必须为 boolean');
    }
  });
}

// Assign distinct questions to all rule slots, including overlapping chapter/subject ranges.
// Reassigning a previously chosen question avoids falsely rejecting an otherwise feasible draw.
function rulesCanDrawDistinct(rules) {
  const slots = [];
  for (const rule of rules) {
    const pool = questions.filter(q => q.type === rule.type && within(q.category, rule.category));
    if (rule.count > pool.length || slots.length + rule.count > questions.length) return false;
    for (let n = 0; n < rule.count; n++) slots.push(pool);
  }
  const owner = new Map();
  function assign(slot, visited) {
    for (const q of slots[slot]) {
      if (visited.has(q)) continue;
      visited.add(q);
      if (!owner.has(q) || assign(owner.get(q), visited)) { owner.set(q, slot); return true; }
    }
    return false;
  }
  return slots.every((_, index) => assign(index, new Set()));
}
const papers = [];
const paperTitles = new Set();
for (const { file, data } of documents) {
  if (!Array.isArray(data.papers)) continue;
  data.papers.forEach((paper, index) => {
    const at = `${file}.papers[${index}]`;
    if (!object(paper)) { fail(at, '试卷必须为对象'); return; }
    papers.push(paper);
    if (!nonempty(paper.title)) fail(at, '试卷标题为空');
    if (paperTitles.has(paper.title)) fail(at, '试卷标题重复');
    paperTitles.add(paper.title);
    const category = referenceCategory(paper.category, file, at);
    if (category && category.level !== 1) fail(at, '试卷所属分类必须为一级考试');
    if (!Number.isInteger(paper.durationMin) || paper.durationMin < 20 || paper.durationMin > 60) fail(at, '示例卷时长须为 20–60 分钟的整数');
    if (!nonempty(paper.description)) fail(at, '试卷说明为空');
    let total = 0;
    if (paper.mode === 'FIXED') {
      if (!Array.isArray(paper.questions) || !paper.questions.length) fail(at, '固定卷必须有题');
      if (paper.rules !== undefined) fail(at, '固定卷不能同时含 rules');
      const seen = new Set();
      for (const item of Array.isArray(paper.questions) ? paper.questions : []) {
        if (!object(item)) { fail(at, '固定卷条目必须为对象'); continue; }
        const q = references.get(item.ref);
        if (!q) fail(at, `固定卷题目 ref 不存在：${String(item.ref)}`);
        else {
          if (q.file > file) fail(at, `题目 ${item.ref} 的定义应在试卷之前`);
          if (!within(q.category, paper.category)) fail(at, `题目 ${item.ref} 不属于本考试`);
        }
        if (seen.has(item.ref)) fail(at, `固定卷题目重复：${item.ref}`);
        seen.add(item.ref);
        if (!Number.isFinite(item.score) || item.score <= 0) fail(at, '分值必须是正数');
        else total += item.score;
      }
    } else if (paper.mode === 'RULE') {
      if (!Array.isArray(paper.rules) || !paper.rules.length) fail(at, '规则卷必须有规则');
      if (paper.questions !== undefined) fail(at, '规则卷不能同时含 questions');
      const validRules = [];
      for (const rule of Array.isArray(paper.rules) ? paper.rules : []) {
        if (!object(rule)) { fail(at, '规则必须为对象'); continue; }
        referenceCategory(rule.category, file, at);
        if (!within(rule.category, paper.category)) fail(at, '规则范围不属于本考试');
        if (!types.includes(rule.type)) fail(at, '规则题型非法');
        if (!Number.isInteger(rule.count) || rule.count < 1) fail(at, '规则题量须为正整数');
        if (!Number.isFinite(rule.score) || rule.score <= 0) fail(at, '规则分值须为正数');
        else if (Number.isInteger(rule.count) && rule.count > 0) total += rule.count * rule.score;
        if (types.includes(rule.type) && Number.isInteger(rule.count) && rule.count > 0) {
          const available = questions.filter(q => q.type === rule.type && within(q.category, rule.category)).length;
          if (available < rule.count) fail(at, `规则题量不足：${rule.category}/${rule.type} need=${rule.count} available=${available}`);
          validRules.push(rule);
        }
      }
      if (!rulesCanDrawDistinct(validRules)) fail(at, '规则联合题量不足（去重及重叠范围检查失败）');
    } else fail(at, 'mode 必须是 FIXED 或 RULE');
    if (!Number.isFinite(paper.passScore) || paper.passScore <= 0 || paper.passScore > total) fail(at, '及格线必须大于 0 且不超过总分');
    else if (paper.passScore / total < 0.55 || paper.passScore / total > 0.65) fail(at, '示例卷及格线须约为总分 60%（55%–65%）');
  });
}

const expectedSubjects = {
  cs408: ['数据结构', '计算机组成原理', '操作系统', '计算机网络'],
  java: ['基础语法', '面向对象', '集合与泛型', '异常与 IO', '多线程'],
  db: ['关系模型', 'SQL 查询', '事务与并发', '索引与优化'],
};
if (roots.length !== 3 || Object.keys(expectedSubjects).some(root => !roots.includes(root))) fail('题库', '必须有 cs408/java/db 三个考试');
for (const [root, names] of Object.entries(expectedSubjects)) {
  const actual = [...categories.values()].filter(c => c.parent === root).map(c => c.name);
  for (const name of names) if (!actual.includes(name)) fail(root, `缺少科目：${name}`);
  if (actual.length !== names.length) fail(root, '科目数与 spec 不符');
  for (const mode of ['FIXED', 'RULE']) if (!papers.some(p => p.category === root && p.mode === mode)) fail(root, `缺少 ${mode} 试卷`);
}
for (const c of categories.values()) {
  const count = questions.filter(q => within(q.category, c.key)).length;
  if (c.level === 3 && count < 8) fail(c.key, `章节题量不足 8，实际 ${count}`);
  if (c.parent === 'cs408' && count < 45) fail(c.key, `408 科目题量不足 45，实际 ${count}`);
}
for (const [root, minimum] of [['java', 90], ['db', 60]]) {
  const count = questions.filter(q => within(q.category, root)).length;
  if (count < minimum) fail(root, `题量不足 ${minimum}，实际 ${count}`);
}
if (questions.length < 330) fail('题库', `总题量不足 330，实际 ${questions.length}`);
if (papers.length < 6) fail('题库', `试卷不足 6，实际 ${papers.length}`);
const judge = questions.filter(q => q.type === 'JUDGE');
const trueCount = judge.filter(q => q.answer?.[0] === 'T').length;
if (Math.abs(trueCount * 2 - judge.length) > 1) fail('题库', '判断题正确/错误数量应各半');
const codeCounts = Object.fromEntries(['java', 'python', 'c', 'sql'].map(language => [language, questions.filter(q => q.type === 'FILL' && typeof q.stem === 'string' && q.stem.includes(`\x60\x60\x60${language}\n`)).length]));
if (codeCounts.java < 20) fail('题库', 'Java 输出填空题不足 20');
if (codeCounts.python + codeCounts.c < 5) fail('题库', 'C/Python 输出填空题不足 5');
if (codeCounts.sql < 5) fail('题库', 'SQL 结果填空题不足 5');
for (const [type, min, max] of [['SINGLE', .45, .65], ['MULTIPLE', .15, .25], ['JUDGE', .10, .20], ['FILL', .05, .20]]) {
  const rate = questions.filter(q => q.type === type).length / questions.length;
  if (rate < min || rate > max) fail('题库', `${type} 占比 ${Math.round(rate * 100)}% 偏离题型配比要求`);
}
console.log(`题库目录：${directory}`);
for (const root of roots) {
  const pool = questions.filter(q => within(q.category, root));
  console.log(`考试 ${categories.get(root)?.name}：${pool.length} 题，题型 ${JSON.stringify(countBy(pool, q => q.type))}`);
}
for (const c of categories.values()) if (c.level === 2 || c.level === 3) console.log(`${c.level === 2 ? '科目' : '  章节'} ${c.key}：${questions.filter(q => within(q.category, c.key)).length}`);
console.log(`总题数 ${questions.length}；各题型 ${JSON.stringify(countBy(questions, q => q.type))}`);
console.log(`难度 ${JSON.stringify(countBy(questions, q => q.difficulty))}；判断 T=${trueCount} F=${judge.length - trueCount}`);
console.log(`输出填空题 ${JSON.stringify(codeCounts)}；实际执行证据需另核对 qb-verify，静态校验不冒充实跑。`);
console.log(`试卷 ${papers.length}；模式 ${JSON.stringify(countBy(papers, p => p.mode))}`);
for (const warning of warnings) console.warn(`WARN ${warning}`);
console.log(`选项相关性人工复核警告：${warnings.length}（仅提示，不改变退出码）`);
if (errors.length) {
  for (const error of errors) console.error(`ERROR ${error}`);
  console.error(`FAIL：${errors.length} 个错误`);
  process.exitCode = 1;
} else console.log('PASS：题库结构、引用、数量与试卷校验全部通过。');
