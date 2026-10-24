# lab_reserve · 实验室预约管理系统

面向高校实验室的**资源（场地 / 设备）在线预约与运维管理系统**。

覆盖完整业务闭环：

```
预约提交 → 管理员审核 → 冲突排队 → 候补通知 → 调整/取消
        → 设备报修 → 派单维修 → 验收归档
        → 消息通知 → 数据统计 → 操作审计
```

---

## 一、技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3 · MyBatis-Plus · Spring Security(JWT) |
| 数据库 | MySQL 8（utf8mb4 / `tb_` 前缀 / 24 张表） |
| 缓存与队列 | Redis 7（排队 ZSet · 收件箱 ZSet · 邮件 Redis Stream）+ Redisson（分布式锁） |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus · Pinia · ECharts |
| 建表 | Flyway（`V1__init_schema.sql`） |
| 对象存储 | 阿里云 OSS（报修故障截图） |
| 邮件 | SMTP（QQ 邮箱授权码） |
| 部署 | Docker Compose 一键启动 |
| 接口文档 | Knife4j（OpenAPI 3） |

---

## 二、快速开始

### 1. 准备环境

- Docker ≥ 24 与 Docker Compose v2
- 本地开发额外需要：JDK 17、Node ≥ 18

### 2. 配置凭据

```bash
cp .env.example .env
```

然后编辑 `.env`，把 `change-me-*` 全部替换成真实值：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | 自己定 |
| `REDIS_PASSWORD` | 自己定 |
| `JWT_SECRET` | `openssl rand -base64 48` 生成 |
| `OSS_*` | 阿里云 OSS 的 endpoint / bucket / AccessKey |
| `MAIL_USERNAME` | 你的邮箱 |
| `MAIL_PASSWORD` | **SMTP 授权码**（16 位），不是登录密码 |

> ⚠️ `.env` 已被 `.gitignore` 忽略，**绝对不要提交**。

### 3. 一键启动

```bash
docker compose up -d --build
docker compose ps          # 4 个容器都应为 healthy
```

| 服务 | 地址 |
|---|---|
| 前端 | http://localhost |
| 后端接口 | http://localhost:8080 |
| 接口文档（Knife4j） | http://localhost:8080/doc.html |
| 健康检查 | `POST http://localhost:8080/api/system/health` |

### 4. 全新重置（会清空数据）

```bash
docker compose down -v     # -v 连带删除数据卷
docker compose up -d --build
```

启动时后端会自动：Flyway 建 24 张表 → 灌入演示种子数据。

---

## 三、本地开发（不走 Docker）

```bash
# 1) 只起中间件
docker compose up -d mysql redis

# 2) 后端（首次会自动下载 Maven 3.9.9）
cd backend
./mvnw spring-boot:run

# 3) 前端
cd frontend
npm install
npm run dev        # http://localhost:6173，/api 通过 vite proxy 转发到 8080
```

> 本机没有 Maven 也没关系，仓库自带 Maven Wrapper（`./mvnw`）。

---

## 四、目录结构

```
lab_reserve/
├─ docker-compose.yml        一键启动编排
├─ .env.example              环境变量模板（真实值放 .env，不提交）
├─ docker/                   部署配置
│   ├─ mysql/init/           建库脚本（容器首启执行）
│   ├─ mysql/my.cnf
│   ├─ redis/redis.conf      AOF + noeviction
│   └─ nginx/default.conf    前端静态 + /api 反代
├─ backend/                  Spring Boot 3（详见下方分层）
├─ frontend/                 Vue 3 + TS
├─ docs/                     交付文档（需规 / 数据库设计 / 接口 / 部署）
└─ instructs/                给协作者的说明文档（数据库表说明等）
```

### 后端分层

```
backend/src/main/java/com/labreserve/
├─ common/            通用基础设施（与业务无关）
│   ├─ annotation/      自定义注解（@OpLog、@Sensitive）
│   ├─ aop/             切面（操作日志）
│   ├─ constant/        常量（CommonConst、RedisKeyConst）
│   ├─ enums/           枚举（OperationModule、OperationType、SensitiveType）
│   ├─ exception/       业务异常 + 全局异常处理器
│   └─ result/          Result<T>、PageResult、PageQuery、ResultCode
├─ config/            Spring 配置（MP / Redis / Jackson / Knife4j / 线程池 / Security / DataSeeder）
├─ util/              工具类（静态工具 + Spring 助手组件：分布式锁、OSS、脱敏）
├─ model/             数据模型
│   ├─ entity/          数据库实体（@TableName）
│   ├─ dto/             入参
│   └─ vo/              出参
├─ controller/        只做参数校验 + 调 service
├─ service/           接口
│   └─ impl/          实现（事务边界）
├─ mapper/            MyBatis-Plus Mapper（不写 XML）
└─ resources/
    ├─ db/migration/         Flyway 建表脚本
    ├─ data/                 种子数据（启动时判空灌入）
    └─ mapper/               .gitkeep 占位
```

---

## 五、分支与提交规范

| 分支 | 用途 | 规则 |
|---|---|---|
| `master` | 可部署版本 | **⚠️ 绝对不直接提交**。只接受 `dev` 的 `--no-ff` 合并，来源必须是「已验收的迭代」，合并后打 tag |
| `dev` | 日常开发 | 所有开发 commit 都提在这里 |

**发版流程**（每个迭代验收通过后执行一次）：

```bash
git switch dev && git push
git switch master
git merge --no-ff dev
git tag -a v0.2.0 -m "迭代 1：认证与权限"
git push --follow-tags
git switch dev
```

> `master` 的起点是一个空提交（`chore: 初始化仓库`），此后所有内容都通过上面的合并进入。

提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)，scope 用模块名：

```
feat(reservation): 实现预约提交与冲突判定
fix(queue): 修复候补超时未写入禁入记录的问题
docs: 补全数据库设计说明书
```

---

## 六、功能模块与完成度

> **当前进度**：迭代 0–8 全部完成并提交在 `dev`（`master` 仍是迭代 0 的可部署版本，等验收后合并）。
> 7 个业务模块均可用，验收脚本见 `scripts/acceptance/`。

| # | 模块 | 主要能力 | 状态 |
|---|---|---|---|
| 1 | 线上预约管理 | 预检 / 提交（并发判冲）/ 审核 / 取消 / 释放 / 调整申请（24h 窗口 + 双快照）/ 学生实验课表 | ✅ |
| 2 | 基础信息管理 | 用户 CRUD（脱敏 + `revealPhone` 留痕）/ 班级 / 实验室 / 设备 / 字典 / 资源变更留痕 / 可用时段 | ✅ |
| 3 | 预约排队与候补 | Redis ZSet 排队、实时顺位、候补通知 + 倒计时、超时/放弃 7 天禁入、自动顺延 | ✅ |
| 4 | 设备运维管理 | 报修（OSS 传图）/ 复核 / 派单 / 维修进度 / 完成 / 验收归档 | ✅ |
| 5 | 通知管理 | 站内信 + 收件箱角标、Redis Stream 邮件投递、失败重试 + 死信、投递记录 | ✅ |
| 6 | 数据统计 | 总览 / 占用率 / 实验类型占比 / 成功率 / 资源占比 / 趋势 / 5 种 Excel 导出 | ✅ |
| 7 | 权限与日志 | 精简 RBAC（权限是数据 + 缓存）、菜单按权限下发、操作日志（含导出）、系统运行状态 | ✅ |

## 六之二、交付文档

| 文档 | 内容 |
|---|---|
| [`docs/01-需求规格说明书.md`](docs/01-需求规格说明书.md) | 角色、模块、业务规则、状态机、**并发控制（K10）**、需求变更 |
| [`docs/02-数据库设计说明书.md`](docs/02-数据库设计说明书.md) | 24 张业务表、索引与约束、Redis 数据分工、容量估算 |
| [`docs/03-接口文档.md`](docs/03-接口文档.md) | 接口清单（约 104 个）、错误码、冲突码、调试说明 |
| [`docs/04-部署与运维手册.md`](docs/04-部署与运维手册.md) | 环境要求、`.env` 配置、一键启动、常见问题、备份恢复、验收脚本 |
| [`docs/problem.md`](docs/problem.md) | 每个迭代「最核心的两个难题」档案（现象/根因/解法/证据） |
| [`docs/rag-design.md`](docs/rag-design.md) | RAG 智能问答模块设计（独立模块） |

## 六之三、演示账号

| 账号 | 角色 | 密码 |
|---|---|---|
| `admin` | 系统管理员 | `123456` |
| `labadmin` | 实验室管理员 | `123456` |
| `repair01` / `repair02` | 维修人员 | `123456` |
| `tea1001` ~ `tea1004` | 教师 | `123456` |
| `stu2021001` ~ `stu2021008` | 学生 | `123456` |

> 邮件通知默认关闭（`notify_email=0`），可在「个人中心」开启。

---

## 七、常见问题

**Q：`docker compose up` 后 backend 一直 unhealthy？**
先看日志：`docker compose logs backend --tail 200`。多数是 `.env` 没填、或 MySQL 还在初始化（首次约需 30 秒）。

**Q：改了表结构没生效？**
Flyway 已执行过的迁移不会重跑。开发期改结构请新增 `V12__xxx.sql`（当前已到 V11）；想推倒重来用 `docker compose down -v`。

**Q：Redis 里的数据重启后丢了？**
检查 `appendonly yes` 是否生效：`docker exec -it lab-reserve-redis redis-cli -a <密码> INFO persistence`。

**Q：邮件发不出去？**
QQ 邮箱必须用 **SMTP 授权码**（设置 → 账户 → 开启 SMTP 服务后生成），不是登录密码。
