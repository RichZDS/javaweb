-- 错题连续答对几次自动移出，每人可调（1–10），默认 3。见 docs/api.md 第 2、4 节。
ALTER TABLE app_user ADD COLUMN wrong_remove_threshold INT NOT NULL DEFAULT 3;
