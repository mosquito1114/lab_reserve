# lab_reserve · 开发约定（AGENTS.md）

> 本文件是**仓库级约定**，AI 助手与协作者都必须遵守；改动代码前先读这里。
> 需求、设计、接口、迭代计划的**正式交付版**在 `docs/`，协作者速查在 `instructs/`；
> 本机协作稿（`agents/`）不进 Git，只作开发过程记录。

---

## 1. 技术栈与目录

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.3 · MyBatis-Plus · Spring Security(JWT) · Redisson（分布式锁） · Flyway · EasyExcel · Knife4j |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus · Pinia · axios · ECharts |
| 存储 | MySQL 8（24 张业务表 + 6 张 RAG 表，统一 `tb_` 前缀）· Redis 7（排队 ZSet / 收件箱 / 邮件 Stream / 分布式锁） |
| 部署 | Docker Compose（`docker-compose.yml`） |

```
backend/src/main/java/com/labreserve/
├─ common/   annotation(@OpLog/@Sensitive) · aop · constant · enums · exception · result
├─ config/   MP / Redis / Redisson / Jackson / Knife4j / Async / Security / Properties
├─ security/ JWT（Provider/Filter）· PermissionService · @RequirePerm + 切面 · MenuService
├─ util/     工具组件（RedisUtils / RedisLockTemplate / IpUtils / JsonUtils / SensitiveUtils …）
├─ model/    entity · dto · vo
├─ controller/ · service/ · service/impl/ · mapper/
└─ rag/      RAG 模块骨架（默认 RAG_ENABLED=false，独立于业务模块）
frontend/src/  api · stores · router · layouts · views · utils
```

---

## 2. 依赖注入约定（硬性要求 ⚠️）

1. **所有 Spring 组件的成员依赖一律用 `@Autowired` 字段注入**：
   Service、Mapper、配置类、切面、Controller、以及本项目的工具组件。
2. **禁止构造器注入**：不要用 `@RequiredArgsConstructor` + `private final` 字段的写法。
   例外只有「该对象不由 Spring 创建」的情况（见第 3 条），或框架明确要求构造器（如 `@ConfigurationProperties` 的绑定类）。
3. **工具类必须是 IOC 组件（`@Component`）并用 `@Autowired` 注入使用**，
   不要再写「`public final class XxxUtils` + `private XxxUtils(){} + static 方法」然后到处静态调用。
   * 唯一例外：`SensitiveSerializer` 由 Jackson 用无参构造创建，拿不到注入，只能用
     `SensitiveUtils.staticMask(...)` / `staticMaskInText(...)` 静态兜底；这两个方法也只允许它调用。
   * `static final` 常量、枚举、值对象不受此约定限制。
4. 需要「构造后校验/初始化」时用 `@PostConstruct`（例如 `JwtTokenProvider#init` 校验密钥长度）。

```java
// ✅ 正确：字段注入 + 工具类注入
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SensitiveUtils sensitiveUtils;

    @Autowired
    private SecurityUtils securityUtils;
}

// ❌ 禁止：构造器注入
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;          // 不允许
}

// ❌ 禁止：工具类写成静态调用
public final class IpUtils {
    private IpUtils() {}
    public static String getClientIp(HttpServletRequest request) { ... }
}
// ✅ 正确：@Component + 实例方法 + @Autowired 注入
```

---

## 3. 后端编码约定

- **统一响应**：所有接口返回 `Result<T>`；业务异常一律 `throw new BizException(...)` / `BizException.badRequest|forbidden|notFound|conflict`，由 `GlobalExceptionHandler` 转换；
  **HTTP 状态码 == 响应体 `code`**，业务冲突统一 409。
- **接口风格**：一律 `POST` + JSON body（唯一例外 `/api/file/upload` 用 multipart），路径 `/api/{资源}/{动作}`。
- **权限**：接口上打 `@RequirePerm(PermissionConst.XXX)`；权限是**数据**（`tb_role_permission`），界面可勾选，改完由 `PermissionService` 让 Redis 缓存失效。
- **操作日志**：所有写操作（新增/修改/删除/审核/派单/验收/导出）必须打 `@OpLog(module, type, content)`；
  入参含密码的接口设 `saveParam = false`；日志只增不改不删、异步写入。
- **并发控制**：需要加锁的接口统一用 `RedisLockTemplate.execute(...)`（底层 Redisson `RLock`），禁止散落的 `try/finally`。
- **分页**：请求体继承 `PageQuery`，响应 `PageResult<T>`，`pageSize ≤ 100`。
- **数据约定**：表名 `tb_` 前缀；**逻辑外键**（不用数据库物理外键）；**反冗余**（能 JOIN 得到就不落库）；
  业务主体表不物理删除（用 `status`）；`created_at/updated_at` 由数据库列默认值维护（不用 `MetaObjectHandler`）。
- **安全红线**：任何表、日志、导入文件**禁止出现身份证号**；手机号明文存储、接口层脱敏；
  查看完整手机号必须写 `QUERY_PHONE` 操作日志；凭据只走 `.env`（不进 Git）。

---

## 4. 开发与调试（不要用 Docker 跑后端）

本机开发固定三件套：**数据库走 Docker，后端走 Maven，前端走 npm**（Docker 全量构建慢，只在交付验收时用）。

```bash
./dev.sh start      # 一键：docker 起 mysql+redis → 后端 mvnw spring-boot:run → 前端 npm run dev
./dev.sh status     # 查看三个进程/容器状态
./dev.sh logs backend
./dev.sh stop       # 停后端 + 前端（数据库容器保留）
./dev.sh down       # 连数据库容器一起停
```

手工方式（等价）：

```bash
docker compose up -d mysql redis          # 只起中间件
cd backend && ./mvnw spring-boot:run      # 8080，热重启用 IDE 或 Ctrl+C 重跑
cd frontend && npm run dev                # 6173，/api 由 vite proxy 转发到 8080
```

| 用途 | 地址 |
|---|---|
| 前端（dev） | http://localhost:6173 |
| 后端 | http://localhost:8080 |
| 接口文档 Knife4j | http://localhost:8080/doc.html |
| 健康检查 | `POST http://localhost:8080/api/system/health` |

**构建验证**（每次提交前）：

```bash
cd backend  && ./mvnw -B clean package -DskipTests && ./mvnw -B test
cd frontend && npm run build && npm run type-check
```

- 连接参数来自根目录 `.env`（`MYSQL_HOST=localhost` 等），`dev.sh` 会自动 `source .env`。
- 数据库重建（会清空数据并重新灌种子）：`docker compose down -v && ./dev.sh start`。
- 改了表结构要**新增 Flyway 迁移**（`V2__xxx.sql`），不要改已执行过的 `V1__init_schema.sql`。

---

## 5. Git 与提交规范

| 分支 | 用途 | 规则 |
|---|---|---|
| `dev` | 日常开发 | 所有提交都提到这里 |
| `master` | 可部署版本 | **禁止直接提交**，只接受 `dev` 的 `--no-ff` 合并（来源必须是已验收的迭代），合并后打 tag |

- 提交信息遵循 **Conventional Commits**，scope 用模块名：`feat(queue): 实现候补顺延`、`fix(auth): 修复黑名单未生效`、`docs: 补全接口文档`。
- **每次提交需双方确认**；提交前贴出 `git diff --stat` 与 commit message。
- 不进 Git 的内容：`agents/`、`.env`、`backend/target/`、`frontend/node_modules/`、`frontend/dist/`、`logs/`。

---

## 6. 迭代与验收

- 迭代划分、任务清单、验收标准见本机 `agents/plan.md`（9 个迭代，每个迭代结束出「自测报告 + 验收清单」，全绿才合并 `master`）。
- 需求/数据库/接口的正式说明见 `instructs/数据库表说明.md` 与 `docs/`（`docs/` 随迭代 8 收尾补齐）。
- 需求变更一律先记录、再评估影响面、最后改代码，不口头改。

---

## 7. 当前实现状态（单一事实来源 · 新会话先读这一节）

> 最后更新：2026-10-21（迭代 2 完成）。**本节与代码冲突时以代码为准，并回来更新本节。**

### 7.1 迭代进度

| 迭代 | 内容 | 状态 |
|---|---|---|
| 0 | 工程骨架 + Docker + Flyway 建表 + 种子数据 | ✅ 已发布（`master` `f0e26d6`，tag `v0.1.0`） |
| 1 | 认证与权限（JWT + 精简 RBAC + 角色权限页 + 操作日志页） | ✅ 已完成，提交在 `dev`（**未合 master**） |
| 2 | 用户管理（用户 CRUD / 班级 / 手机号脱敏 / 个人中心） | ✅ 已完成，提交在 `dev`（**未合 master**） |
| 3 | 消息通知（站内信 + Redis Stream 邮件投递 + 重试/死信） | ✅ 已完成，提交在 `dev`（未合 master；QQ 真发待授权码，本地调试 SMTP 已验通） |
| 4 | 资源管理（实验室 / 设备 / 字典 / 可用时段 / 变更留痕） | ✅ 已完成，提交在 `dev`（未合 master） |
| 5 | 预约管理（预检 / 提交 / 审核 / 调整申请 + 并发判冲） | ✅ 已完成，提交在 `dev`（未合 master） |
| 6 | 排队与候补（Redis ZSet + 超时顺延 + 7 天禁入） | ✅ 已完成，提交在 `dev`（未合 master） |
| 7 | 设备运维（报修 → 派单 → 维修 → 验收 + OSS 传图） | ✅ 已完成，提交在 `dev`（未合 master；OSS 凭据在 `.env`） |
| 8 | 统计报表 + Excel 导出 + 交付文档 | ✅ 已完成，提交在 `dev`（未合 master；`docs/` 四份文档已补齐） |
| R0 | RAG 模块骨架（另一批提交，`RAG_ENABLED=false`） | 🟡 骨架在 `dev`，未合 master |

### 7.2 已实现的接口（全部 `POST` + JSON）

| 模块 | 路径 |
|---|---|
| 认证 | `/api/auth/login` `refresh` `logout` `get_info` `update_password` |
| 系统 | `/api/system/health`（公开，仅 status）、`/api/system/monitor`（`system:monitor:view`，仅 SYS_ADMIN） |
| 用户 | `/api/user/get_page` `get_info` `create` `update` `update_status` `update_self` `get_self_profile` `list_maintainer` `list_option` |
| 班级 | `/api/class/get_list` `get_page` `create` `update` |
| 角色/权限 | `/api/role/get_list` `get_permission_list` `update_permission`、`/api/permission/get_list` |
| 操作日志 | `/api/operation_log/get_page` `get_info` `export` |
| 资源 | 字典 `/api/experiment_type/*`、`/api/time_slot/*`（各 4 个）；`/api/laboratory/get_page` `get_info` `create` `update` `update_status` `list_option` `get_availability`；`/api/equipment/*`（同名 7 个）；`/api/resource_log/get_page` |
| 预约 | `/api/reservation/check` `create` `get_page` `get_my_page` `get_pending_page` `get_info` `audit` `cancel` `release` `get_my_timetable` |
| 调整申请 | `/api/reservation_change/create` `get_list_by_reservation` `get_page` `audit` `cancel` |
| 文件 | `/api/file/upload`（multipart，唯一例外；≤5MB，jpg/png/webp，校验文件头魔数） |
| 统计 | `/api/statistics/get_overview` `get_occupancy` `get_experiment_type_ratio` `get_success_rate` `get_resource_ratio` `get_reservation_trend` `export`（`stat:view` / `stat:export`） |
| 运维 | `/api/repair_order/create` `get_page` `get_my_submitted_page` `get_my_assigned_page` `get_info` `review` `assign` `update_progress` `complete` `accept`、`/api/repair_record/get_list` |
| 排队 | `/api/queue/create` `get_my_page` `get_page` `get_info` `get_my_position` `cancel` `confirm` `abandon` `get_notice_list`、`/api/queue_ban/get_my_list` |
| 消息 | `/api/message/get_page` `get_info` `get_unread_count` `read` `read_all` `delete` `clear` `get_delivery_log_page` `send_test_email` `send`（`message:send`，仅 SYS_ADMIN） |

**尚未实现**：无（7 个业务模块全部完成；RAG 为独立模块，默认关闭）。

### 7.3 与最初需求不同的地方（必须知道）

1. **实验室预约主体收窄为教师**（`agents/req.md` §10 变更 1）：`resourceType=1` 只有 `TEACHER` 能约，且必须带 `classIds`；学生只看「我的实验课表」（反查 `tb_reservation_class`）；设备师生都能约。
2. **LAB_ADMIN 权限收窄到本职**（`agents/req.md` §10 变更 2）：只有资源维护 + 预约/调整审批 + 排队调度 + 报修验收；**没有**用户、班级、操作日志、邮件投递记录；查看完整手机号的例外只剩 `SYS_ADMIN`。
3. **角色 / 账号 / 学号工号不可修改**：`/user/update` 不含这些字段，换角色需注销后重建。
4. **邮件通知默认关闭**（`notify_email=0`），由用户在个人中心自行开启；**全部演示账号密码统一 `123456`（含 admin）**。
5. **资源变更必须留痕**：实验室/设备的 `create` / `update` / `update_status` 都强制带 `updateReason`，
   由 `ResourceLogService` 写 `tb_resource_update_log`（before/after 快照）；字典（实验类型/时段）被引用时禁止删除（409）。
6. **预约并发靠双保险**：`lock:reserve:{type}:{resId}:{date}:{slotId}`（Redisson）+ `tb_reservation.uk_reservation_lab_occupy`
   （`occupy_key` 生成列唯一索引，占用集合 `{PENDING, APPROVED, CHANGE_PENDING}`）；锁内判冲后插入，仍撞索引则转 409。
7. **调整申请「通过前不改业务字段」**：只把 `status` 置 `CHANGE_PENDING`，变更内容存 `before_json`/`after_json`，
   审批通过时在锁内校验新时段 → 回写 → 释放旧时段；窗口为「距开始 ≥ 24h」。
8. **候补顺延有唯一入口**：`release` / 预约取消 / 调整审批通过 / 队首超时放弃，都调
   `QueueCandidateService#triggerCandidate`（锁内取队首 → CAS NOTIFIED → 写通知记录 + deadline ZSet → 发消息）；
   确认后**直接生成 APPROVED 预约单**（`from_queue=1`），超时/放弃写 7 天禁入（`tb_queue_ban_record`）。
9. **运维工单状态机严格**：`SUBMITTED → ASSIGNED → REPAIRING → REPAIRED → ACCEPTED`，复核驳回 `REJECTED`、验收不通过 `REOPENED`；
   非法流转 409；维修人只能操作指派给自己的工单（403）；验收通过会写 `archive_time` 并把设备状态置回「正常」；
   **设备维修不联动作废任何预约**（需求 Q23）。
10. **文件只走后端中转**：`POST /api/file/upload`（multipart）→ `AliyunOssTemplate` → 返回 OSS URL；
   校验大小（5MB）/ 后缀白名单 / Content-Type / **文件头魔数**；凭据只在 `.env`（`OSS_*`），bucket 需公共读。
11. **统计口径在后端**：5 种报表与页面共用同一份聚合（`StatisticsServiceImpl`），
    保证「页面数字 = Excel 数字」；占用率口径 = 已占用时段 / 开放时段，无筛选时缓存 10 分钟。
12. **消息只走「统一入口 + Redis Stream」**：业务代码一律调 `MessageService.send(...)`，禁止直接写 `tb_message` 或手动 `XADD`；
   邮件状态机 `NULL 未入队 → SENDING 投递中 → OK / DEAD`，`max-retry`（默认 3）是**总投递次数上限**，超过即进 `stream:msg:email:dead`。

### 7.4 权限与数据现状

- 权限是数据（`tb_role_permission`），缓存 `lab:reserve:perm:role:{roleId}`，权限变更后由 `PermissionService` 删缓存。
- 现存权限点 **59 个**（含 `system:monitor:view`、`message:send`）；各角色：SYS_ADMIN 23 / LAB_ADMIN 27 / STUDENT 15 / TEACHER 15 / MAINTAINER 6。
- 定时任务：邮件消费/重投/死信、候补超时扫描、**预约过期扫描（`APPROVED → EXPIRED`，每小时）**、
  未读数校正、已删消息清理、排队 ZSet 对账（见 `docs/04-部署与运维手册.md` §8）。
- 迁移：`V1` 建表 → `V2` RAG 6 表 + 6 权限 → `V3` 修 RAG 授权 → `V4` `system:monitor:view` → `V5` 收窄 LAB_ADMIN → `V6` `message:send`。**新增结构请写 V7+，不要改已执行过的脚本**。
- 表：24 张业务表（`tb_` 前缀）+ 6 张 RAG 表。
- 手机号：明文存储、接口层脱敏；本人看自己明文；`revealPhone=true` + `user:view-phone` 才给明文，并写 `QUERY_PHONE` 日志。
- 不物理删除：用户/实验室/设备用 `status`，预约用状态流转；操作日志只增不改不删。

### 7.5 演示账号与数据

- **密码全部 `123456`**（含 `admin`）；`notify_email` 默认 0（关闭）。
- 账号：`admin`(SYS_ADMIN)、`labadmin`(LAB_ADMIN)、`repair01`/`repair02`(MAINTAINER)、`stu2021001~08`、`tea1001~04`；验收脚本会额外建 `t2025001` / `tt2001` / `tm2001` 与班级 `测试2501`。
- 资源：种子含 5 个实验类型、5 个时段、12 个实验室、84 台设备、360 条演示预约
  （日期以 `CURDATE()` 为基准：实验室 今天-2 ~ 今天+1、设备 今天-1 ~ 今天+1，每天 6 条 PENDING 供审核演示）；
  验收脚本会额外建 `LAB-99` / `ZY-99-001` / `TEST_TYPE`。
- 重置数据：`docker compose down -v && ./dev.sh start`（Flyway + 种子自动重建）。

### 7.6 验收脚本与文档位置

- 验收脚本：`scripts/acceptance/iter1_auth.py`（62 项）、`iter2_user.py`（48 项）、`iter3_message.py`（59 项）、
  `iter4_resource.py`（62 项）、`iter5_reservation.py`（47 项）；先 `./dev.sh start`，再 `python3 scripts/acceptance/iterX.py`。
- 并发压测：`python3 scripts/stress/reservation_concurrency.py`（默认 50 并发，验收要求「恰好 1 条成功、其余 409」）。
- 验收脚本补 `iter7_repair.py`（42 项，会真实上传图片到 OSS）、`iter8_statistics.py`（35 项，含 SQL 对账与导出）；OSS 联调依赖 `.env` 的 `OSS_ENDPOINT/BUCKET/ACCESS_KEY_ID/ACCESS_KEY_SECRET`。
- 候补超时演示：用 `QUEUE_CANDIDATE_TIMEOUT_MINUTES=1 QUEUE_SCAN_INTERVAL_MS=3000 ./dev.sh restart` 重启后跑
  `python3 scripts/acceptance/iter6_queue.py`（37 项全跑，含超时 → 禁入 → 顺延）；默认 30 分钟配置下该组 SKIP。
- 邮件联调：`python3 scripts/tools/dev_smtp.py --port 1025`（本地调试 SMTP，邮件落盘 `/tmp/dev_smtp/*.eml`），后端需 `MAIL_HOST=127.0.0.1 MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_SSL=false`；QQ 真发只需改 `.env` 的 `MAIL_USERNAME`/`MAIL_PASSWORD`（授权码）后重启。
- **交付文档**：`docs/01-需求规格说明书.md`、`02-数据库设计说明书.md`、`03-接口文档.md`、`04-部署与运维手册.md`（与实现同步维护）；
- 需求/设计/接口/计划：`agents/req.md`、`agents/design.md`、`agents/api.md`、`agents/plan.md`（**本机协作稿，不进 Git**，各自带「变更记录 / 自测报告」）。
- 协作者速查：`instructs/数据库表说明.md`；交付文档在 `docs/`（迭代 8 补齐），RAG 模块另有 `docs/rag-design.md`。
- **迭代难题档案**：`docs/problem.md` —— 每个迭代记录「最核心的两个难题」（现象/根因/解法/证据/教训），迭代完成后更新；遇到「这个坑好像见过」先翻它。
