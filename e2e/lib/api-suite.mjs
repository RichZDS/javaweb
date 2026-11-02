import test from 'node:test';
import { available, serviceError } from './http.mjs';

export async function apiSuite(stage) {
  if (process.env.QA_COLLECT_ONLY === '1') {
    return (name, run, options = {}) => test(name, { ...options, skip: `${stage} 仅收集，未执行业务` }, run);
  }
  if (!await available()) {
    test(`${stage} 前置：本地 API 服务已启动`, () => {
      const error = new Error(serviceError()); error.stack = error.message; throw error;
    });
    return () => {};
  }
  return (name, run, options = {}) => test(name, { timeout: 60000, ...options }, run);
}
