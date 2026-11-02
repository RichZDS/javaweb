-- 选项顺序打乱（docs/api.md 第 4、6 节“选项打乱”）。只加列。
-- 练习：每道单选、多选题在这一次里的显示顺序，JSON {"<题目 id>": ["C","A","D","B"]}（按显示顺序排的原标签）；不打乱为 NULL。
ALTER TABLE practice_session ADD COLUMN option_orders TEXT;
-- 试卷：开考时是否打乱选项。
ALTER TABLE exam_paper ADD COLUMN shuffle_options BOOLEAN NOT NULL DEFAULT FALSE;
