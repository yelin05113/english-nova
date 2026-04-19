# English Nova 后端接口文档

> 基于 `BackEnd-EnglishNova/distributed` 代码手动梳理生成。
> 框架：Spring Boot 4.0.0 + Spring Cloud Gateway，Java 17。

---

## 1. 概述

本项目为分布式微服务架构，通过 **Gateway** 统一暴露接口。

- **Gateway 基地址**：`http://localhost:8080`
- **内部服务端口**：8081 ~ 8086（不直接对外暴露）

### 服务路由映射

| 路由前缀 | 目标服务 | 说明 |
|---------|---------|------|
| `/api/auth/**` | auth-service | 认证服务（注册/登录/JWT） |
| `/api/system/**` | system-service | 系统概览 |
| `/api/study/**` | study-service | 学习日程与进度 |
| `/api/search/**` | search-service | 单词搜索与公共词库导入 |
| `/api/imports/**` | import-service | 导入任务与文件上传 |
| `/api/wordbooks/**` | quiz-service | 词书与词汇 |
| `/api/quiz/**` | quiz-service | 测验会话 |

---

## 2. 认证方式

部分接口需要认证。Gateway 通过 `GatewayJwtFilter` 校验 JWT Token，并将用户信息注入请求头。

**客户端调用方式**：
在请求头中携带 `Authorization: Bearer <accessToken>`。

> 标注为「需认证」的接口，必须通过 Gateway 并携带有效 JWT；否则将返回 401/403。

---

## 3. 通用响应格式

所有接口均返回统一包装对象 `ApiResponse<T>`：

```json
{
  "success": true,
  "data": {},
  "message": "ok",
  "timestamp": "2026-04-15T10:00:00+08:00"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | `boolean` | 请求是否成功 |
| `data` | `T` | 业务数据，失败时可能为 `null` |
| `message` | `string` | 提示信息 |
| `timestamp` | `string` | ISO-8601 时间戳 |

---

## 4. 枚举定义

| 枚举 | 值 | 说明 |
|------|-----|------|
| **QuizMode** | `CN_TO_EN` | 中译英 |
| | `EN_TO_CN` | 英译中 |
| | `MIXED` | 混合模式 |
| **PromptType** | `CN_TO_EN` | 提示类型：中译英 |
| | `EN_TO_CN` | 提示类型：英译中 |
| **WordImportPlatform** | `BAICIZHAN` | 百词斩 |
| | `BUBEIDANCI` | 不背单词 |
| | `SHANBAY` | 扇贝单词 |
| | `ANKI` | Anki |
| **VocabularyVisibility** | `PUBLIC` | 公开 |
| | `PRIVATE` | 私有 |

---

## 5. 接口列表

### 5.1 Auth Service（认证服务）

基地址前缀：`/api/auth`

#### 5.1.1 用户注册
- **Method**：`POST`
- **URL**：`/api/auth/register`
- **认证**：不需要
- **请求体**：`RegisterRequest`

```json
{
  "username": "string",
  "email": "string",
  "password": "string"
}
```

- **响应**：`ApiResponse<AuthTokenResponse>`

#### 5.1.2 用户登录
- **Method**：`POST`
- **URL**：`/api/auth/login`
- **认证**：不需要
- **请求体**：`LoginRequest`

```json
{
  "account": "string",
  "password": "string"
}
```

- **响应**：`ApiResponse<AuthTokenResponse>`

#### 5.1.3 获取当前登录用户信息
- **Method**：`GET`
- **URL**：`/api/auth/me`
- **认证**：需要
- **响应**：`ApiResponse<AuthUserDto>`

---

### 5.2 System Service（系统服务）

基地址前缀：`/api/system`

#### 5.2.1 系统概览
- **Method**：`GET`
- **URL**：`/api/system/overview`
- **认证**：不需要
- **响应**：`ApiResponse<SystemOverviewDto>`

---

### 5.3 Study Service（学习服务）

基地址前缀：`/api/study`

#### 5.3.1 今日学习日程
- **Method**：`GET`
- **URL**：`/api/study/agenda`
- **认证**：需要
- **响应**：`ApiResponse<StudyAgendaDto>`

#### 5.3.2 学习进度统计
- **Method**：`GET`
- **URL**：`/api/study/progress`
- **认证**：需要
- **响应**：`ApiResponse<StudyProgressDto>`

---

### 5.4 Search Service（搜索服务）

基地址前缀：`/api/search`

#### 5.4.1 单词搜索
- **Method**：`GET`
- **URL**：`/api/search/words?q={keyword}`
- **认证**：不需要
- **Query 参数**：
  - `q`（string，可选，默认 `""`）：搜索关键词
- **响应**：`ApiResponse<WordSearchResponseDto>`

#### 5.4.2 搜索建议
- **Method**：`GET`
- **URL**：`/api/search/suggestions?q={keyword}`
- **认证**：不需要
- **Query 参数**：
  - `q`（string，可选，默认 `""`）：搜索关键词
- **响应**：`ApiResponse<List<SearchSuggestionDto>>`

#### 5.4.3 单词详情
- **Method**：`GET`
- **URL**：`/api/search/words/{entryId}`
- **认证**：不需要
- **Path 参数**：
  - `entryId`（long）：词条 ID
- **响应**：`ApiResponse<WordDetailDto>`

#### 5.4.4 公共词库导入
- **Method**：`POST`
- **URL**：`/api/search/public-catalog/import`
- **认证**：需要
- **请求体**：`PublicCatalogImportRequest`

```json
{
  "words": ["apple", "banana"],
  "refreshExisting": true
}
```

- **响应**：`ApiResponse<PublicCatalogImportResultDto>`

---

#### 5.4.5 High-frequency public catalog warmup
- **Method**: `POST`
- **URL**: `/api/search/public-catalog/import-high-frequency`
- **Auth**: required
- **Request body**: `PublicCatalogImportJobRequest`

```json
{
  "sourceName": "high-frequency-5000",
  "limit": 5000,
  "batchSize": 150,
  "refreshExisting": false
}
```

- **Behavior**: creates a persistent background job. The worker reads `public-catalog/high-frequency-5000.txt`, imports in batches, skips existing public words unless refresh is enabled, and only stores entries whose word, phonetic, Chinese meaning, example, category, and audio URL are complete.
- **Response**: `ApiResponse<PublicCatalogImportJobDto>`

#### 5.4.6 Public catalog import job status
- **Method**: `GET`
- **URL**: `/api/search/public-catalog/import-jobs/{jobId}`
- **Auth**: required
- **Response**: `ApiResponse<PublicCatalogImportJobDto>`

#### 5.4.7 Retry failed public catalog import items
- **Method**: `POST`
- **URL**: `/api/search/public-catalog/import-jobs/{jobId}/retry-failed`
- **Auth**: required
- **Response**: `ApiResponse<PublicCatalogImportJobDto>`

#### 5.4.8 Cancel public catalog import job
- **Method**: `POST`
- **URL**: `/api/search/public-catalog/import-jobs/{jobId}/cancel`
- **Auth**: required
- **Response**: `ApiResponse<PublicCatalogImportJobDto>`

### 5.5 Import Service（导入服务）

基地址前缀：`/api/imports`

#### 5.5.1 获取导入预设
- **Method**：`GET`
- **URL**：`/api/imports/presets`
- **认证**：不需要
- **响应**：`ApiResponse<List<ImportPresetDto>>`

#### 5.5.2 获取导入任务列表
- **Method**：`GET`
- **URL**：`/api/imports/tasks`
- **认证**：需要
- **响应**：`ApiResponse<List<ImportTaskDto>>`

#### 5.5.3 创建导入任务
- **Method**：`POST`
- **URL**：`/api/imports/tasks`
- **认证**：需要
- **请求体**：`ImportTaskRequest`

```json
{
  "platform": "BAICIZHAN",
  "sourceName": "string",
  "estimatedCards": 100
}
```

- **响应**：`ApiResponse<ImportTaskDto>`

#### 5.5.4 文件导入
- **Method**：`POST`
- **URL**：`/api/imports/files`
- **Content-Type**：`multipart/form-data`
- **认证**：需要
- **请求参数**：
  - `platform`（`WordImportPlatform`）：导入平台
  - `sourceName`（string，可选）：来源名称
  - `file`（MultipartFile）：导入文件
- **响应**：`ApiResponse<ImportTaskDto>`

---

### 5.6 Quiz Service（测验服务）

基地址前缀：`/api/wordbooks`、`/api/quiz`

#### 5.6.1 获取词书列表
- **Method**：`GET`
- **URL**：`/api/wordbooks`
- **认证**：需要
- **响应**：`ApiResponse<List<WordbookSummaryDto>>`

#### 5.6.2 获取词书词汇列表
- **Method**：`GET`
- **URL**：`/api/wordbooks/{wordbookId}/entries`
- **认证**：需要
- **Path 参数**：
  - `wordbookId`（long）：词书 ID
- **响应**：`ApiResponse<List<VocabularyEntryDto>>`

#### 5.6.3 获取词书进度
- **Method**：`GET`
- **URL**：`/api/wordbooks/{wordbookId}/progress`
- **认证**：需要
- **Path 参数**：
  - `wordbookId`（long）：词书 ID
- **响应**：`ApiResponse<WordbookProgressDto>`

#### 5.6.4 创建测验会话
- **Method**：`POST`
- **URL**：`/api/quiz/sessions`
- **认证**：需要
- **请求体**：`CreateQuizSessionRequest`

```json
{
  "wordbookId": 1,
  "mode": "MIXED"
}
```

- **响应**：`ApiResponse<QuizSessionStateDto>`

#### 5.6.5 获取测验会话状态
- **Method**：`GET`
- **URL**：`/api/quiz/sessions/{sessionId}`
- **认证**：需要
- **Path 参数**：
  - `sessionId`（string）：会话 ID
- **响应**：`ApiResponse<QuizSessionStateDto>`

#### 5.6.6 提交测验答案
- **Method**：`POST`
- **URL**：`/api/quiz/sessions/{sessionId}/answers`
- **认证**：需要
- **Path 参数**：
  - `sessionId`（string）：会话 ID
- **请求体**：`QuizAnswerRequest`

```json
{
  "attemptId": 1,
  "selectedOption": "string"
}
```

- **响应**：`ApiResponse<QuizAnswerResultDto>`

---

## 6. DTO 定义

### Auth

#### `RegisterRequest`
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `username` | `string` | 非空，3~32 字符 | 用户名 |
| `email` | `string` | 非空，邮箱格式 | 邮箱 |
| `password` | `string` | 非空，6~64 字符 | 密码 |

#### `LoginRequest`
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `account` | `string` | 非空 | 账号 |
| `password` | `string` | 非空 | 密码 |

#### `AuthTokenResponse`
| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | `string` | JWT 访问令牌 |
| `user` | `AuthUserDto` | 用户信息 |

#### `AuthUserDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 用户 ID |
| `username` | `string` | 用户名 |

---

### System

#### `SystemOverviewDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `productName` | `string` | 产品名称 |
| `theme` | `string` | 主题 |
| `supportedPlatforms` | `List<string>` | 支持的平台列表 |
| `modules` | `List<SystemModuleDto>` | 系统模块列表 |
| `deliveryPhases` | `List<string>` | 交付阶段列表 |

#### `SystemModuleDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `string` | 模块名称 |
| `responsibility` | `string` | 职责说明 |
| `status` | `string` | 状态 |

---

### Study

#### `StudyAgendaDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `newCards` | `int` | 新卡片数量 |
| `reviewCards` | `int` | 复习卡片数量 |
| `listeningCards` | `int` | 听力卡片数量 |
| `estimatedMinutes` | `int` | 预计学习时长（分钟） |
| `focusAreas` | `List<string>` | 今日重点学习领域 |

#### `StudyProgressDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `totalWords` | `int` | 总单词数 |
| `clearedWords` | `int` | 已掌握单词数 |
| `inProgressWords` | `int` | 学习中单词数 |
| `newWords` | `int` | 新单词数 |
| `wordbooks` | `int` | 词书数量 |
| `answeredQuestions` | `int` | 已答题数 |
| `correctAnswers` | `int` | 正确答题数 |
| `accuracyRate` | `int` | 正确率（%） |

---

### Search

#### `WordSearchResponseDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `publicHits` | `List<SearchHitDto>` | 公开库匹配结果 |
| `myHits` | `List<SearchHitDto>` | 我的词书匹配结果 |

#### `SearchHitDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `entryId` | `Long` | 词条 ID |
| `word` | `string` | 单词 |
| `phonetic` | `string` | 音标 |
| `meaningCn` | `string` | 中文释义 |
| `source` | `string` | 来源 |
| `exampleSentence` | `string` | 例句 |
| `category` | `string` | 分类 |
| `visibility` | `string` | 可见性 |
| `importSource` | `string` | 导入来源 |
| `matchPercent` | `int` | 匹配度百分比 |
| `matchType` | `string` | 匹配类型 |

#### `SearchSuggestionDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `entryId` | `Long` | 词条 ID |
| `word` | `string` | 单词 |
| `visibility` | `string` | 可见性 |
| `matchPercent` | `int` | 匹配度百分比 |
| `matchType` | `string` | 匹配类型 |

#### `WordDetailDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `entryId` | `long` | 词条 ID |
| `ownerUserId` | `Long` | 拥有者用户 ID |
| `wordbookId` | `long` | 所属词书 ID |
| `wordbookName` | `string` | 词书名称 |
| `word` | `string` | 单词 |
| `phonetic` | `string` | 音标 |
| `meaningCn` | `string` | 中文释义 |
| `exampleSentence` | `string` | 例句 |
| `category` | `string` | 分类 |
| `difficulty` | `int` | 难度 |
| `visibility` | `string` | 可见性 |
| `source` | `string` | 来源 |
| `sourceName` | `string` | 来源名称 |
| `importSource` | `string` | 导入来源 |
| `audioUrl` | `string` | 音频 URL |

#### `PublicCatalogImportRequest`
| 字段 | 类型 | 说明 |
|------|------|------|
| `words` | `List<string>` | 要导入的单词列表 |
| `refreshExisting` | `boolean` | 是否刷新已存在的词条 |

#### `PublicCatalogImportResultDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `requestedWords` | `int` | 请求导入的单词数 |
| `importedWords` | `int` | 成功导入数 |
| `updatedWords` | `int` | 更新数 |
| `skippedWords` | `int` | 跳过数 |
| `failedWords` | `int` | 失败数 |
| `imported` | `List<string>` | 成功导入的单词列表 |
| `updated` | `List<string>` | 更新的单词列表 |
| `skipped` | `List<string>` | 跳过的单词列表 |
| `failed` | `List<string>` | 失败的单词列表 |

---

### Import

#### `ImportPresetDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `platform` | `WordImportPlatform` | 平台 |
| `title` | `string` | 标题 |
| `description` | `string` | 描述 |
| `acceptedExtensions` | `List<string>` | 接受的文件扩展名 |
| `mappedFields` | `List<string>` | 映射字段 |

#### `ImportTaskRequest`
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `platform` | `WordImportPlatform` | 非空 | 导入平台 |
| `sourceName` | `string` | 非空 | 来源名称 |
| `estimatedCards` | `int` | 最小 1 | 预计卡片数 |

#### `ImportTaskDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | `string` | 任务 ID |
| `wordbookId` | `Long` | 生成的词书 ID |
| `platform` | `WordImportPlatform` | 平台 |
| `sourceName` | `string` | 来源名称 |
| `estimatedCards` | `int` | 预计卡片数 |
| `importedCards` | `int` | 已导入卡片数 |
| `status` | `string` | 任务状态 |
| `queuedAt` | `string` (OffsetDateTime) | 排队时间 |
| `finishedAt` | `string` (OffsetDateTime) | 完成时间 |
| `queueName` | `string` | 队列名称 |

---

### Quiz

#### `WordbookSummaryDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 词书 ID |
| `name` | `string` | 词书名称 |
| `platform` | `WordImportPlatform` | 来源平台 |
| `wordCount` | `int` | 总词汇数 |
| `clearedCount` | `int` | 已掌握数 |
| `pendingCount` | `int` | 待学习数 |
| `createdAt` | `string` (OffsetDateTime) | 创建时间 |

#### `VocabularyEntryDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `long` | 词条 ID |
| `word` | `string` | 单词 |
| `phonetic` | `string` | 音标 |
| `meaningCn` | `string` | 中文释义 |
| `exampleSentence` | `string` | 例句 |
| `category` | `string` | 分类 |
| `difficulty` | `int` | 难度 |
| `visibility` | `string` | 可见性 |

#### `WordbookProgressDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `wordbookId` | `long` | 词书 ID |
| `wordCount` | `int` | 总词汇数 |
| `clearedCount` | `int` | 已掌握数 |
| `inProgressCount` | `int` | 学习中数 |
| `pendingCount` | `int` | 待学习数 |

#### `CreateQuizSessionRequest`
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `wordbookId` | `Long` | 非空 | 词书 ID |
| `mode` | `QuizMode` | | 测验模式 |

#### `QuizSessionDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 会话 ID |
| `wordbookId` | `long` | 词书 ID |
| `mode` | `QuizMode` | 测验模式 |
| `totalQuestions` | `int` | 总题数 |
| `answeredQuestions` | `int` | 已答题数 |
| `correctAnswers` | `int` | 正确数 |
| `status` | `string` | 会话状态 |

#### `QuizQuestionDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `attemptId` | `long` | 答题尝试 ID |
| `promptType` | `PromptType` | 提示类型 |
| `promptText` | `string` | 提示文本 |
| `options` | `List<string>` | 选项列表 |
| `progress` | `int` | 当前进度 |
| `totalQuestions` | `int` | 总题数 |

#### `QuizSessionStateDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `session` | `QuizSessionDto` | 会话信息 |
| `currentQuestion` | `QuizQuestionDto` | 当前题目 |

#### `QuizAnswerRequest`
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `attemptId` | `Long` | 非空 | 答题尝试 ID |
| `selectedOption` | `string` | 非空 | 选中的答案选项 |

#### `QuizAnswerResultDto`
| 字段 | 类型 | 说明 |
|------|------|------|
| `correct` | `boolean` | 是否正确 |
| `correctOption` | `string` | 正确答案 |
| `remainingQuestions` | `int` | 剩余题数 |
| `session` | `QuizSessionDto` | 会话信息 |
| `nextQuestion` | `QuizQuestionDto` | 下一题 |

---

## 附录：关键源码路径

| 类别 | 路径 |
|------|------|
| Gateway 路由 | `distributed/gateway-service/src/main/resources/application.yml` |
| AuthController | `distributed/auth-service/src/main/java/.../auth/controller/AuthController.java` |
| SystemController | `distributed/system-service/src/main/java/.../system/SystemController.java` |
| StudyController | `distributed/study-service/src/main/java/.../study/controller/StudyController.java` |
| SearchController | `distributed/search-service/src/main/java/.../search/controller/SearchController.java` |
| ImportController | `distributed/import-service/src/main/java/.../importservice/controller/ImportController.java` |
| QuizController | `distributed/quiz-service/src/main/java/.../quiz/controller/QuizController.java` |
| 通用 DTO | `distributed/english-nova-common/src/main/java/.../shared/dto/` |
| 通用枚举 | `distributed/english-nova-common/src/main/java/.../shared/enums/` |
