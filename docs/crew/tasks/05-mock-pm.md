# 05 · 演示数据：产品经理（qzd-pm-5b1e）

按 `docs/mock-data.md` 写 `backend/src/main/resources/mock/` 下四个文件：`users.json`（300 人）、`questions.json`（80–120 道原创新题）、`papers.json`（4–6 份规则卷）、`texts.json`。

- 先 `git merge main`。分类名称照抄 `backend/src/main/resources/seed/` 里的，路径用「考试 / 科目 / 章节」。
- 第 2 节「真实感要求」逐条做到；写完自己核一遍比例（考试人数、画像、注册月份分布、换目标、禁用、头像）并写进报审。
- 新题规则同 seed-format：原创、看代码写输出的题必须真跑过（本机有 JDK 17、Python、MySQL 13306）、每题有解析。
- 只提交数据文件；生成过程中如用了临时脚本，不要提交。
- 交付分两次报审：M1 = `users.json` + `texts.json`（后端要先拿它联调），M2 = `questions.json` + `papers.json`。
- 不连线上，不碰别人的目录。
