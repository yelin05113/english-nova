# English Nova

English Nova 是一个英语词汇学习项目，包含 React 前端、Spring Boot 分布式后端、Docker 本地基础设施，以及词库导入和 AI 例句/音频增强脚本。

## 项目结构

```text
english-nova/
├─ FrontEnd-EnglishNova/          # React + TypeScript + Vite 前端
├─ BackEnd-EnglishNova/
│  ├─ distributed/                # 当前主要后端，多模块 Spring Boot 服务
│  └─ docs/API.md                 # 旧接口整理文档，部分内容存在编码问题
├─ docker/                        # MySQL 初始化、迁移 SQL、Nacos 配置、seeder 脚本
├─ scripts/                       # 本地启动、词库生成、导入、补数据脚本
├─ upload/                        # 本地上传文件和示例音频挂载目录
├─ .local/                        # 本地生成的公开词库分片，默认不提交
├─ .env.example                   # 环境变量模板
├─ docker-compose.yml             # 本地 Docker 编排
└─ README.md
```

## 技术栈

前端：

- React 19
- TypeScript 6
- Vite 8
- React Router 7
- Tailwind CSS 4

后端：

- Java 17
- Spring Boot 4.0.0
- Spring Cloud 2025.1.0
- Spring Cloud Alibaba / Nacos
- Spring Cloud Gateway
- MyBatis-Plus
- MySQL 8.4
- Redis
- RabbitMQ
- Elasticsearch 8.19

## 后端服务

后端位于 `BackEnd-EnglishNova/distributed`，是一个 Maven 多模块项目：

| 模块 | 端口 | 说明 |
| --- | ---: | --- |
| `gateway-service` | `8080` 容器内，默认映射到 `8087` | 统一入口、路由、JWT 校验、内部签名头 |
| `auth-service` | `8085` | 注册、登录、当前用户、资料、头像、偏好设置 |
| `system-service` | `8081` | 系统概览 |
| `study-service` | `8082` | 今日学习计划和进度统计 |
| `search-service` | `8083` | 单词搜索、公开词书、公开词库导入、AI 英语问答、例句音频 |
| `import-service` | `8084` | 百词斩、不背单词、扇贝、Anki 等词书文件导入 |
| `quiz-service` | `8086` | 词书、单词本、测验会话、答题和选项刷新 |
| `english-nova-common` | - | 公共 DTO、枚举、异常、鉴权工具和文本工具 |

网关路由主要包括：

- `/api/auth/**`
- `/api/system/**`
- `/api/study/**`
- `/api/search/**`
- `/api/public-wordbooks/**`
- `/api/imports/**`
- `/api/wordbooks/**`
- `/api/word-notebooks/**`
- `/api/quiz/**`
- `/upload/images/**`

## 前端功能

前端位于 `FrontEnd-EnglishNova`，主要页面和能力包括：

- 首页和应用布局
- 登录、注册、用户资料和头像
- 公开词库/词书浏览
- 单词搜索和单词详情
- 导入中心
- 单词本
- 测验练习
- 学习进度
- AI 英语问答辅助

路由入口在 `FrontEnd-EnglishNova/src/router/index.tsx`，前端请求封装在 `FrontEnd-EnglishNova/src/api/client.ts`。

## 本地环境变量

先复制模板：

```powershell
Copy-Item .env.example .env
```

然后按本机情况修改 `.env`。至少需要关注：

- `JWT_SECRET`
- `NACOS_AUTH_TOKEN`
- `MYSQL_*`
- `RABBITMQ_*`
- `OPENAI_API_KEY` 或 `DEEPSEEK_API_KEY`
- `OPENAI_MODEL` 或 `DEEPSEEK_MODEL`

`.env` 是本机私有配置，不应提交；`.env.example` 是可提交模板。

## 使用 Docker 启动

启动基础设施并执行初始化：

```powershell
.\scripts\start-infra.ps1
```

这个脚本会执行：

```powershell
docker compose --env-file .env up -d mysql nacos redis rabbitmq elasticsearch
docker compose --env-file .env up seeder
```

`seeder` 会做两件事：

- 在 MySQL 未初始化时导入 `docker/mysql/init/001-schema.sql` 和 `docker/mysql/init/002-seed.sql`
- 将 `docker/nacos/configs/*.yaml` 发布到 Nacos

启动完整 Docker 应用：

```powershell
docker compose --env-file .env up --build -d
```

常用访问地址：

- 前端：`http://localhost:3000`
- 网关：`http://localhost:8087`
- Nacos 控制台：`http://localhost:8080`
- RabbitMQ 管理页：`http://localhost:15672`
- Elasticsearch：`http://localhost:9200`

## 本地开发

前端开发：

```powershell
.\scripts\run-frontend-local.ps1
```

或手动执行：

```powershell
cd FrontEnd-EnglishNova
npm install
npm run dev -- --config vite.config.mjs --configLoader native --host 0.0.0.0 --port 3000
```

后端编译：

```powershell
cd BackEnd-EnglishNova\distributed
mvn -q -DskipTests package
```

启动单个后端服务示例：

```powershell
cd BackEnd-EnglishNova\distributed
mvn -q -pl search-service -am spring-boot:run
```

项目里也保留了若干 search-service 专用脚本：

- `scripts/start-search-text-only.ps1`
- `scripts/start-search-deepseek-text-only.ps1`
- `scripts/start-search-enrichment.ps1`
- `scripts/launch-search-text-only.ps1`
- `scripts/launch-search-enrichment.ps1`

这些脚本会读取根目录 `.env`，用于公开词库例句、释义和音频补全任务。

## 公开词库和 ECDICT

项目内置了高频 ECDICT 资源：

- `BackEnd-EnglishNova/distributed/search-service/src/main/resources/public-catalog/ecdict-high-frequency-10000.tsv`
- `BackEnd-EnglishNova/distributed/search-service/src/main/resources/public-catalog/ecdict-high-frequency-5000.tsv`

全量 ECDICT 分片默认生成到：

```text
.local/public-catalog/
```

相关脚本：

- `scripts/catalog/build_ecdict_high_frequency.py`
- `scripts/catalog/build_ecdict_full_catalog.py`
- `scripts/catalog/import_ecdict_public_wordbooks.py`
- `scripts/catalog/import_local_public_wordbooks.py`
- `scripts/catalog/queue_next_ecdict_chunk.py`
- `scripts/catalog/queue_ecdict_130k.ps1`

`.local/` 是本地缓存目录，已在 `.gitignore` 中忽略。

## 数据库和配置

Docker 初始化材料位于 `docker/`：

- `docker/mysql/init/001-schema.sql`：基础表结构
- `docker/mysql/init/002-seed.sql`：种子数据
- `docker/mysql/migrations/*.sql`：后续迁移和补丁
- `docker/nacos/configs/*.yaml`：各服务 Nacos 配置
- `docker/seeder/seed.sh`：MySQL 和 Nacos 初始化脚本

MySQL、Redis、RabbitMQ、Elasticsearch 都通过 Docker volume 持久化，volume 名称定义在 `docker-compose.yml`。

## 构建和检查

前端：

```powershell
cd FrontEnd-EnglishNova
npm run build
npm run lint
```

后端：

```powershell
cd BackEnd-EnglishNova\distributed
mvn -q -DskipTests package
```

## 许可证

本项目使用 MIT License，见 `LICENSE`。
