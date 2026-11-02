-- 在线刷题学习 APP 初始表结构。
-- 同时跑在 MySQL 8 和 H2（MODE=MySQL）上：只用两边都支持的语法，JSON 一律存 TEXT。
-- 所有时间存 UTC。

CREATE TABLE app_user (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    username           VARCHAR(20)  NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    nickname           VARCHAR(30)  NOT NULL,
    avatar_url         VARCHAR(255),
    role               VARCHAR(10)  NOT NULL DEFAULT 'USER',      -- USER / ADMIN
    status             VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE / DISABLED
    target_category_id BIGINT,
    last_active_at     DATETIME(3),
    created_at         DATETIME(3)  NOT NULL,
    updated_at         DATETIME(3)  NOT NULL
);
CREATE UNIQUE INDEX uk_user_username ON app_user (username);

CREATE TABLE category (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id  BIGINT,
    name       VARCHAR(50) NOT NULL,
    level      INT         NOT NULL,                 -- 1 考试 / 2 科目 / 3 章节
    sort       INT         NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL
);
CREATE INDEX idx_category_parent ON category (parent_id, sort);

CREATE TABLE question (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id      BIGINT       NOT NULL,          -- 必须是 3 级分类
    type             VARCHAR(10)  NOT NULL,          -- SINGLE / MULTIPLE / JUDGE / FILL
    stem             TEXT         NOT NULL,          -- Markdown
    options_json     TEXT,                           -- [{"key":"A","text":"..."}]
    answer_json      TEXT,                           -- 选择/判断: ["A","C"]；填空: [["int","Integer"],["5"]]
    fill_options     VARCHAR(100),                   -- {"ignoreCase":false,"ignoreSpaces":false}
    analysis         TEXT,                           -- Markdown
    difficulty       INT          NOT NULL DEFAULT 3,
    tags             VARCHAR(255),                   -- 逗号分隔
    image_url        VARCHAR(255),
    source_note      VARCHAR(255),                   -- 题目来源或授权说明
    status           VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT / PUBLISHED / ARCHIVED
    version          INT          NOT NULL DEFAULT 1,
    sort             INT          NOT NULL DEFAULT 0, -- 顺序练习用
    created_at       DATETIME(3)  NOT NULL,
    updated_at       DATETIME(3)  NOT NULL
);
CREATE INDEX idx_question_category ON question (category_id, status, sort);

CREATE TABLE practice_session (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    mode          VARCHAR(12) NOT NULL,              -- RANDOM / SEQUENTIAL / WRONG / FAVORITE / DAILY / TAG
    recite_mode   BOOLEAN     NOT NULL DEFAULT FALSE,
    params_json   TEXT,                              -- 开始时的请求参数
    question_ids  TEXT        NOT NULL,              -- 开始时固定的题目 id 列表 [1,2,3]
    current_index INT         NOT NULL DEFAULT 0,
    status        VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS / COMPLETED
    root_category_id BIGINT,                         -- 所属考试（一级分类）
    sequential_category_id BIGINT,                   -- 顺序练习的章节
    daily_date    DATE,                              -- 每日一练日期（北京时间）
    started_at    DATETIME(3) NOT NULL,
    completed_at  DATETIME(3),
    updated_at    DATETIME(3) NOT NULL
);
CREATE INDEX idx_session_user ON practice_session (user_id, status, started_at);
CREATE UNIQUE INDEX uk_session_daily ON practice_session (user_id, root_category_id, daily_date);

-- 每一次正式作答（练习提交、考试结算）。统计全部从这里来。
CREATE TABLE answer_record (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT      NOT NULL,
    question_id          BIGINT      NOT NULL,
    source               VARCHAR(10) NOT NULL,       -- PRACTICE / EXAM
    source_id            BIGINT      NOT NULL,       -- practice_session.id 或 exam_record.id
    user_answer          TEXT,
    answered             BOOLEAN     NOT NULL DEFAULT TRUE,
    is_correct           BOOLEAN     NOT NULL,
    category_id_snapshot BIGINT      NOT NULL,
    root_category_id     BIGINT      NOT NULL,
    time_spent_sec       INT,
    graded_at            DATETIME(3) NOT NULL
);
CREATE UNIQUE INDEX uk_answer_source ON answer_record (source, source_id, question_id);
CREATE INDEX idx_answer_user_time ON answer_record (user_id, root_category_id, graded_at);
CREATE INDEX idx_answer_user_question ON answer_record (user_id, question_id, graded_at);

CREATE TABLE wrong_question (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT      NOT NULL,
    question_id       BIGINT      NOT NULL,
    wrong_count       INT         NOT NULL DEFAULT 1,
    correct_streak    INT         NOT NULL DEFAULT 0,
    last_wrong_answer TEXT,
    last_wrong_at     DATETIME(3) NOT NULL,
    removed           BOOLEAN     NOT NULL DEFAULT FALSE,
    removed_at        DATETIME(3),
    created_at        DATETIME(3) NOT NULL
);
CREATE UNIQUE INDEX uk_wrong_user_question ON wrong_question (user_id, question_id);
CREATE INDEX idx_wrong_user_recent ON wrong_question (user_id, removed, last_wrong_at);

CREATE TABLE favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    question_id BIGINT       NOT NULL,
    note        VARCHAR(200),
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL
);
CREATE UNIQUE INDEX uk_favorite_user_question ON favorite (user_id, question_id);

CREATE TABLE exam_paper (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(100) NOT NULL,
    category_id  BIGINT       NOT NULL,              -- 一级分类（考试）
    description  TEXT,
    mode         VARCHAR(10)  NOT NULL,              -- FIXED / RULE
    duration_min INT          NOT NULL,
    total_score  INT          NOT NULL,
    pass_score   INT          NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'OFFLINE',  -- ONLINE / OFFLINE
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL
);

CREATE TABLE paper_question (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id    BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    score       INT    NOT NULL,
    sort        INT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_paper_question ON paper_question (paper_id, question_id);

CREATE TABLE paper_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id    BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL,                -- 2 级或 3 级分类，含子章节
    type        VARCHAR(10) NOT NULL,
    count       INT         NOT NULL,
    score       INT         NOT NULL,                -- 每题分值
    sort        INT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_paper_rule ON paper_rule (paper_id, sort);

CREATE TABLE exam_record (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT      NOT NULL,
    paper_id          BIGINT      NOT NULL,
    question_snapshot TEXT        NOT NULL,          -- 开考时的题目、标准答案、分值
    total_score       INT         NOT NULL,
    pass_score        INT         NOT NULL,
    started_at        DATETIME(3) NOT NULL,
    deadline_at       DATETIME(3) NOT NULL,
    submitted_at      DATETIME(3),
    score             INT,
    passed            BOOLEAN,
    status            VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS / FINISHED
    finish_reason     VARCHAR(12)                                  -- MANUAL / TIMEOUT
);
CREATE INDEX idx_exam_user ON exam_record (user_id, paper_id, status);
CREATE INDEX idx_exam_deadline ON exam_record (status, deadline_at);

CREATE TABLE exam_answer (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id   BIGINT      NOT NULL,
    question_id BIGINT      NOT NULL,
    user_answer TEXT,
    version     INT         NOT NULL DEFAULT 0,
    marked      BOOLEAN     NOT NULL DEFAULT FALSE,
    received_at DATETIME(3) NOT NULL,
    is_correct  BOOLEAN,
    score       INT
);
CREATE UNIQUE INDEX uk_exam_answer ON exam_answer (record_id, question_id);

CREATE TABLE question_feedback (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    question_id BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL,               -- ANSWER_WRONG / STEM_WRONG / ANALYSIS_UNCLEAR / OTHER
    content     VARCHAR(500) NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING',  -- PENDING / RESOLVED / REJECTED
    reply       VARCHAR(500),
    created_at  DATETIME(3)  NOT NULL,
    handled_at  DATETIME(3)
);
CREATE INDEX idx_feedback_status ON question_feedback (status, created_at);
