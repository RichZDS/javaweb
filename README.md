# 在线刷题学习 APP

毕业设计选题「在线刷题学习 APP 的设计与实现」。面向计算机专业学生：题库分类、随机刷题、错题本、模拟考试、成绩统计，Android 和 iOS 都能安装。

![CI](https://github.com/RichZDS/javaweb/actions/workflows/ci.yml/badge.svg)

## 组成

| 目录 | 是什么 | 技术 |
| --- | --- | --- |
| `backend/` | 后端接口，全部业务逻辑（抽题、判分、计时、统计） | Java 17、Spring Boot 3.5、Spring Security + JWT、MyBatis-Plus、Flyway、MySQL 8 |
| `admin/` | 网页管理后台：分类、题目、试卷、纠错、用户、看板 | Vue 3、Vite、TypeScript、Element Plus、Pinia |
| `app/` | 手机 App，一套代码打 Android 和 iOS 安装包 | uni-app（Vue 3）、TypeScript、uni-ui、uCharts |
| `docs/` | 接口约定、题库文件格式、功能说明 | — |
| `deploy/` | 服务器部署脚本和 Nginx 配置 | — |

接口以 [`docs/api.md`](docs/api.md) 为准。功能说明见 [`docs/spec.md`](docs/spec.md)。

## 本地运行

后端（默认 `dev` 配置用内存 H2 数据库，启动时自动导入示例题库和管理员账号）：

```bash
cd backend
mvn spring-boot:run
# 接口文档：http://localhost:8080/swagger-ui.html
```

管理后台：

```bash
cd admin
npm ci
npm run dev   # 默认把 /api 代理到 http://localhost:8080
```

手机 App（浏览器里先调试 H5 版）：

```bash
cd app
npm ci
npm run dev:h5
```

打 Android / iOS 安装包：用 HBuilderX 打开 `app/` 目录，选“发行 → 原生 App-云打包”。iOS 需要苹果开发者账号的证书和描述文件，见 [`docs/release.md`](docs/release.md)。

## CI

每次推送到 `main` 和每个 PR 都会跑 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)：

- 后端：H2 和 MySQL 8 各跑一遍全部测试，打出 jar。
- 管理后台：类型检查、单元测试、构建。
- 手机 App：类型检查、单元测试、H5 构建、App 资源构建。

构建产物在每次运行的 Artifacts 里下载。
