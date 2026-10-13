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
| 数据库 | MySQL 8（utf8mb4 / `tb_` 前缀 / 23 张表） |
| 缓存与队列 | Redis 7（排队 ZSet · 收件箱 ZSet · 邮件 Redis Stream · 分布式锁） |
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

启动时后端会自动：Flyway 建 23 张表 → 灌入演示种子数据。

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
npm run dev        # http://localhost:5173，/api 通过 vite proxy 转发到 8080
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
└─ docs/                     交付文档（需规 / 数据库设计 / 接口 / 部署）
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
│   └─ result/          R<T>、PageResult、PageQuery、ResultCode
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

| 分支 | 用途 |
|---|---|
| `master` | **只放可部署版本**，仅在迭代验收通过后从 `dev` 合并 |
| `dev` | 日常开发 |

提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)，scope 用模块名：

```
feat(reservation): 实现预约提交与冲突判定
fix(queue): 修复候补超时未写入禁入记录的问题
docs: 补全数据库设计说明书
```

---

## 六、功能模块

| # | 模块 | 状态 |
|---|---|---|
| 1 | 线上预约管理（提交 / 审核 / 调整申请） | 待实现 |
| 2 | 基础信息管理（用户 / 实验室 / 设备 / 资源日志） | 待实现 |
| 3 | 预约排队与候补（Redis ZSet + 候补顺延） | 待实现 |
| 4 | 设备运维管理（报修 → 派单 → 维修 → 验收） | 待实现 |
| 5 | 通知管理（站内信 + 邮件 Stream + 失败重发） | 待实现 |
| 6 | 数据统计（占用率 / 实验类型占比 / 成功率 / 导出） | 待实现 |
| 7 | 权限与日志（精简 RBAC + 操作日志） | 待实现 |

---

## 七、常见问题

**Q：`docker compose up` 后 backend 一直 unhealthy？**
先看日志：`docker compose logs backend --tail 200`。多数是 `.env` 没填、或 MySQL 还在初始化（首次约需 30 秒）。

**Q：改了表结构没生效？**
Flyway 已执行过的迁移不会重跑。开发期改结构请新增 `V2__xxx.sql`；想推倒重来用 `docker compose down -v`。

**Q：Redis 里的数据重启后丢了？**
检查 `appendonly yes` 是否生效：`docker exec -it lab-reserve-redis redis-cli -a <密码> INFO persistence`。

**Q：邮件发不出去？**
QQ 邮箱必须用 **SMTP 授权码**（设置 → 账户 → 开启 SMTP 服务后生成），不是登录密码。
