# Smart Voice

AI 口语练习系统，支持用户注册登录、场景化英语对话、语音识别、AI 回复、语音合成、逐句反馈、课后总结和学习进度统计。

demo演示：https://www.bilibili.com/video/BV1mfEt63Em8/?spm_id_from=333.1387.list.card_archive.click&vd_source=ffaaf88706f94ac540b64bc146162a7f
## 功能特性

- 用户认证：注册、登录、刷新 Token、查看与更新个人资料
- 场景练习：内置面试、点餐、会议、旅行、日常沟通等英语口语场景
- 对话练习：支持文本/语音对话，按会话保存历史记录
- 流式返回：支持 AI 回复流式输出，降低对话等待时间
- 语音能力：支持 ASR 语音识别与 TTS 语音合成
- 逐句反馈：每轮对话生成纠错、表达建议、发音与流利度反馈
- 课后总结：生成总分、发音、流利度、语法、词汇、理解度等学习报告
- 学习进度：支持按日、周、月统计练习次数与能力趋势
- 前端体验：React 单页应用，包含注册页、历史会话、场景选择、反馈面板和课后总结入口

## 技术栈

### 后端

- Java 17
- Spring Boot 3.2.5
- Spring Security + JWT
- MyBatis-Plus
- MySQL
- Spring AI Alibaba / DashScope
- Aliyun NLS SDK（ASR/TTS）
- Springdoc OpenAPI

### 前端

- React 19
- TypeScript
- Vite
- CSS Modules 风格的全局样式组织

## 项目结构

```text
smart-voice
├── frontend/                         # React + Vite 前端
│   ├── src/
│   │   ├── App.tsx                   # 主页面与交互逻辑
│   │   ├── api.ts                    # 前端 API 封装
│   │   ├── audio.ts                  # 录音相关工具
│   │   ├── styles.css                # 页面样式
│   │   └── types.ts                  # 前端类型定义
│   └── vite.config.ts                # Vite 配置，代理 /api 到后端 8080
├── src/main/java/com/smartvoice/
│   ├── user/                         # 用户、认证、资料
│   ├── scenario/                     # 练习场景
│   ├── session/                      # 会话与历史记录
│   ├── chat/                         # AI 对话
│   ├── voice/                        # ASR/TTS/语音对话
│   ├── pronunciation/                # 发音与流利度评估
│   ├── correction/                   # 纠错与表达建议
│   ├── report/                       # 课后总结与学习进度
│   └── common/                       # 通用配置、异常、响应结构
├── src/main/resources/
│   ├── application.yml               # 后端配置
│   └── db/
│       ├── schema.sql                # 数据库表结构
│       └── data.sql                  # 初始练习场景
└── pom.xml                           # Maven 后端依赖
```

## 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm
- MySQL 8.x

## 配置变量

后端配置读取环境变量。启动前至少需要配置数据库、JWT 和 DashScope。

### 必填配置

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
$env:JWT_SECRET="至少 32 字符的 JWT 密钥"
$env:DASHSCOPE_API_KEY="你的 DashScope API Key"
```

### 阿里云 NLS 配置

如果要启用真实 ASR/TTS，需要配置阿里云智能语音交互相关参数：

```powershell
$env:ALIYUN_NLS_ENABLED="true"
$env:ALIYUN_NLS_FALLBACK_ENABLED="false"
$env:NLS_APP_KEY="你的 NLS AppKey"
$env:ALIYUN_AK_ID="你的 AccessKey ID"
$env:ALIYUN_AK_SECRET="你的 AccessKey Secret"
$env:NLS_GATEWAY_URL="wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
$env:NLS_SAMPLE_RATE="16000"
```

可选 TTS 参数：

```powershell
$env:NLS_TTS_VOICE="zhixiaobai"
$env:NLS_TTS_FORMAT="wav"
$env:NLS_TTS_VOLUME="50"
$env:NLS_TTS_SPEECH_RATE="0"
$env:NLS_TTS_PITCH_RATE="0"
```

如暂时不接阿里云 NLS，可以开启兜底模式：

```powershell
$env:ALIYUN_NLS_ENABLED="false"
$env:ALIYUN_NLS_FALLBACK_ENABLED="true"
```

兜底模式下，ASR 会优先使用前端传入的 transcript hint，TTS 会返回静音音频，仅用于本地流程调试。

## 数据库初始化

1. 创建数据库：

```sql
CREATE DATABASE smart_voice DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;
```

2. 导入表结构和初始场景：

```powershell
mysql -u root -p smart_voice < src/main/resources/db/schema.sql
mysql -u root -p smart_voice < src/main/resources/db/data.sql
```

当前 `application.yml` 中 `spring.sql.init.mode=never`，项目启动时不会自动导入 SQL。需要手动导入，或自行改为适合开发环境的初始化方式。

## 启动后端

```powershell
mvn -q -DskipTests compile
mvn spring-boot:run
```

后端默认运行在：

```text
http://localhost:8080
```

OpenAPI JSON：

```text
http://localhost:8080/api-docs
```

## 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认运行在：

```text
http://localhost:5173
```

Vite 已配置代理，前端请求 `/api` 会转发到 `http://localhost:8080`。

## 构建命令

后端：

```powershell
mvn -q -DskipTests package
```

前端：

```powershell
cd frontend
npm run build
```

## 主要接口

### 认证与用户

- `POST /api/v1/auth/register`：注册
- `POST /api/v1/auth/login`：登录
- `POST /api/v1/auth/refresh`：刷新 Token
- `GET /api/v1/users/me`：查看当前用户资料
- `PUT /api/v1/users/me`：更新当前用户资料

### 场景

- `GET /api/v1/scenarios`：获取场景列表
- `GET /api/v1/scenarios/{id}`：获取场景详情
- `GET /api/v1/scenarios/categories`：获取场景分类

### 会话

- `POST /api/v1/sessions`：创建会话
- `GET /api/v1/sessions`：获取当前用户会话列表
- `GET /api/v1/sessions/{id}`：获取会话
- `GET /api/v1/sessions/{id}/detail`：获取会话详情与对话轮次
- `POST /api/v1/sessions/{id}/end`：结束会话

### 对话

- `POST /api/v1/chat/{sessionId}`：发送文本对话
- `POST /api/v1/chat/{sessionId}/stream`：流式文本对话
- `POST /api/v1/chat/{sessionId}/end`：结束对话

### 语音

- `GET /api/v1/voice/nls/status`：查看 NLS 配置状态
- `POST /api/v1/voice/asr`：语音识别
- `POST /api/v1/voice/tts`：语音合成
- `POST /api/v1/voice/dialogue/{sessionId}`：语音对话
- `POST /api/v1/voice/dialogue/{sessionId}/stream`：流式语音对话

### 反馈与报告

- `POST /api/v1/pronunciation/evaluate`：发音与流利度评估
- `POST /api/v1/corrections`：语法纠错与表达建议
- `POST /api/v1/sessions/{sessionId}/report`：生成课后总结
- `GET /api/v1/sessions/{sessionId}/report`：查看课后总结
- `GET /api/v1/progress/overview`：查看学习进度概览

## 评分与反馈说明

- `asrConfidence`：真实 ASR 模式下来自语音识别结果；兜底模式下按是否有音频和文本提示生成模拟置信度
- `pronunciationScore`：根据识别文本、目标文本、单词覆盖度、文本相似度等规则估算
- `fluencyScore`：根据句子长度、语速、停顿近似值和回答完整度估算
- `overallScore`：综合发音、流利度和节奏分计算
- `corrections`：优先调用 LLM 生成自然语言纠错；失败时使用本地规则兜底
- `session report`：基于会话轮次、每轮评分、词汇量和纠错信息汇总课后报告

当前发音评估属于本地规则测评，不等同于专业语音评测服务的音素级评分。如需更精确的音素、重音、韵律、逐词评分，可后续接入专业语音评测 API。

## 常见问题

### 启动时报数据库连接失败

确认 MySQL 已启动，数据库 `smart_voice` 已创建，并且 `DB_USERNAME`、`DB_PASSWORD` 正确。

### 场景列表为空

确认已经导入 `src/main/resources/db/data.sql`。当前项目不会在启动时自动导入初始化数据。

### AI 回复失败

确认 `DASHSCOPE_API_KEY` 已配置，且当前网络环境可以访问 DashScope 服务。

### 语音识别或合成失败

如果使用真实阿里云 NLS，确认 `NLS_APP_KEY`、`ALIYUN_AK_ID`、`ALIYUN_AK_SECRET` 和网关地址配置正确。

如果只是本地调试，可以设置：

```powershell
$env:ALIYUN_NLS_ENABLED="false"
$env:ALIYUN_NLS_FALLBACK_ENABLED="true"
```

### 前端接口请求失败

确认后端运行在 `http://localhost:8080`，前端运行在 `http://localhost:5173`，并且请求路径以 `/api` 开头。

## 开发建议

- 后端接口修改后，优先执行 `mvn -q -DskipTests compile`
- 前端页面修改后，优先执行 `npm run build`
- 数据库结构变更后，同步更新 `src/main/resources/db/schema.sql`
- 新增场景后，同步更新 `src/main/resources/db/data.sql`
- 涉及会话、报告、反馈字段时，注意同步后端 DTO 和前端 `types.ts`
