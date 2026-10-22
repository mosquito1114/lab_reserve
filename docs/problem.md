# 迭代难题档案（docs/problem.md）

> **用途**：每个迭代结束后，记录**当轮最核心的两个难题**——现象、根因、解法、证据、教训。
> 目的不是记流水账，而是让后面接手的人（包括新会话里的 AI）少踩同一条坑。
>
> **维护规则**
> - 每个迭代**只记两个**最难的；其余问题合并进「附：同轮其它坑」一行带过。
> - 必须写「证据」：文件路径 / 迁移版本 / 命令 / 验收脚本断言，避免变成主观感想。
> - 迭代完成并在 `dev` 提交后更新本文件；文末的「待补迭代」按顺序替换成正式条目。
>
> **最后更新**：2026-10-22（迭代 4 完成）

---

## 索引

| 迭代 | 内容 | 两个核心难题 |
|---|---|---|
| 0 | 工程骨架 / Docker / Flyway / 种子数据 | ① 全新库下 Flyway 与种子抢插数据 ② 一键脚本的分词与路径可靠性 |
| 1 | 认证与权限（JWT + RBAC） | ① 权限缓存与权限数据的一致性 ② 框架执行顺序（`@Valid` 早于 AOP） |
| 2 | 用户管理 + 手机号脱敏 | ① 「本人看自己明文」与全局序列化器冲突 ② 登录后菜单为空 |
| 3 | 消息通知 + Redis Stream 邮件 | ① 邮件重试语义（PEL / delivery-count）② 收件箱「Redis 索引 + DB 正文」一致性 |
| 4 | 资源管理 + 变更留痕 | ① 多条件短路式的「可用性」判定 ② 变更快照该存什么 |
| 5–8 | 预约 / 排队 / 运维 / 统计 | 待补（模板见文末） |

---

## 迭代 0：工程骨架（Docker + Flyway + 种子数据）

### 难题 1｜全新库启动时，Flyway 和种子脚本抢着插同一批权限

**现象**：老库升级没事，`docker compose down -v` 之后全新启动反而失败：

```
Duplicate entry 'system:monitor:view' for key 'tb_permission.uk_permission_code'
```

Flyway 的 `V2/V4/V5` 会给 `tb_permission` / `tb_role_permission` 插数据，`DataSeeder` 又会灌 `seed_data.sql`；
两者都假设「表是空的」，谁先执行谁就占位，后来者撞唯一键。

**根因**：把「建库、建表、灌数据」三件事的**职责边界**搞混了，且脚本默认自己独占数据表。幂等不是"加个 try-catch"，而是每一步都要能重复执行。

**解法**：三段职责彻底分开，且每一步都幂等。

| 阶段 | 由谁负责 | 幂等方式 |
|---|---|---|
| 建库 | `docker/mysql/init/*.sql`（容器首次启动） | `CREATE DATABASE IF NOT EXISTS` |
| 建表 + 结构演进 | Flyway `V1…V6` | 版本号只增不改 |
| 灌演示数据 | `DataSeeder`（判空后执行 `seed_data.sql`） | `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` |

`seed_data.sql` 里所有 `INSERT INTO tb_permission` / `tb_role_permission` 一律改成 `INSERT IGNORE`，与迁移脚本的幂等风格对齐。

**证据**
- `backend/src/main/resources/db/migration/V4__add_system_monitor_permission.sql`、`V5`、`V6`（均为 `INSERT IGNORE` 或 `SELECT … JOIN` 授权）
- `backend/src/main/resources/data/seed_data.sql`（`INSERT IGNORE` × 6 处）
- 验证命令：`docker compose down -v && ./dev.sh start` → `flyway_schema_history` 中 V1–V6 全 `success`，权限 59 条、SYS_ADMIN 23 条

**教训**：只要一个脚本可能被"重跑"或"与迁移交叉执行"，就必须写成幂等的；**先约定执行顺序，再写 SQL**。

### 难题 2｜一键脚本的隐藏坑：中文紧跟变量 + 外置盘偶发不可达

**现象**：`./dev.sh` 偶发直接退出，报 `qdrant: unbound variable`；另外 `cd /Users/codingxb/D-Disk/...` 偶尔报 `No such file or directory`。

**根因**
- bash 的变量名边界不含中文：`echo "启动 $qdrant，完成"` 会把 `$qdrant，完成` 整体当变量名，在 `set -u` 下直接报错退出。
- 外置盘（`/Volumes` 类挂载）在休眠/占用时会短暂不可达，属于环境抖动而非代码 bug。

**解法**
- 变量后紧跟非 ASCII 字符时一律写 `${qdrant}`；脚本里 `set -u` 保留（能提前暴露真正的空变量）。
- 需要 `cd` 外置盘的命令加一次重试（`cd X || (sleep 2; cd X)`），并在失败时给出明确提示而不是静默继续。

**证据**：`dev.sh`；本轮多次命令前的 sleep 重试即为该现象的应对。

**教训**：脚本的"偶发失败"多半是**分词 / 引号 / 路径**三个来源之一，先用 `${}` 显式定界，再怀疑环境。

**附：同轮其它坑**：`@SpringBootApplication` 需排除 `UserDetailsServiceAutoConfiguration`，否则启动日志里的 `generated security password` 会被误读成"项目自己生成了密码"。

---

## 迭代 1：认证与权限

### 难题 1｜权限是数据、鉴权走缓存，改完权限"看起来没生效"

**现象**：在角色权限页勾掉某个权限后，被撤权账号仍能调接口；反过来新授权也要等服务重启才生效。

**根因**：鉴权为了性能读 Redis（`lab:reserve:perm:role:{roleId}`），而权限变更写的是 MySQL。**两处数据源各有生命周期**，中间没有失效通道，必然出现"界面改了、缓存没改"。

**解法**
- 权限读写收敛到 `PermissionService` 一个出口：读时 miss 则回源 DB 并回填；`update_permission` 成功后**主动 `DEL`** 该角色缓存。
- 菜单与接口**共用同一份权限码**（`MenuService` 按 `tb_role_permission` 过滤，接口打 `@RequirePerm`），避免前端一套判断、后端一套判断。
- 迁移（如 `V5` 收窄 LAB_ADMIN）直接改库，必须同步清缓存——这一步写进了验收脚本与 `AGENTS.md`。

**证据**
- `security/PermissionService.java`、`security/PermissionAspect.java`、`security/MenuService.java`
- `common/constant/RedisKeyConst#permRoleKey`
- 验收：`iter2_user.py` 里"LAB_ADMIN 调 `/user/*` → 403"的断言，跑前清缓存才稳定

**教训**：**缓存不是问题，"没有失效通道的缓存"才是问题**。凡是"数据可改 + 结果被缓存"的地方，改数据的那一步必须负责让缓存失效。

### 难题 2｜框架执行顺序：`@Valid` 比 AOP 切面更早

**现象**：无权限角色 POST 一个 body 不合法的请求，返回 **400** 而不是预期的 **403**，验收脚本一度假失败。

**根因**：Spring MVC 的参数校验（`@Valid` + `MethodArgumentNotValidException`）发生在**方法调用之前**，而权限切面（`@RequirePerm`）是方法级 AOP。校验失败压根走不到切面，所以"先报 400"是框架的既定行为，不是 bug。

**解法**
- 不在代码里"绕过"这个顺序，而是**把真实行为写进文档与断言**：body 合法时一律 403，body 非法时 400。
- 验收脚本对权限断言**一律构造合法 body**，避免把 400 当成"权限没拦住"。

**证据**：`AGENTS.md` §7 的已知框架行为；`iter1_auth.py` / `iter2_user.py` 的 403 断言均使用合法请求体。

**教训**：**框架的执行顺序决定了错误码优先级**。遇到"错误码不符合直觉"，先确认三层顺序：参数绑定/校验 → AOP → 方法体。

**附：同轮其它坑**：Excel 导出 500（`NoSuchMethodError: UnsynchronizedByteArrayOutputStream.builder()`）是 `commons-io` 被传递依赖降到 2.7，`pom.xml` 显式钉 `2.16.1` 解决——**三方库报"类里有方法但运行期没有"，先查版本被谁降级**。

---

## 迭代 2：用户管理

### 难题 1｜「手机号默认脱敏，但本人看自己是明文」与全局序列化器天生冲突

**现象**：用 Jackson 的 `@Sensitive` 注解 + 自定义 `JsonSerializer` 脱敏后，本人查自己的资料也被打码；想加"本人例外"，序列化器里根本拿不到"当前请求者是谁"。

**根因**：序列化器是**无状态、无请求上下文**的组件（Jackson 用它时不给 Spring 的 `RequestContextHolder`），所以它只能做"无条件脱敏"，做不了"按角色/按归属决定是否脱敏"。

**解法**：把"要不要脱敏"的决策从序列化层上移到**服务层**。
- service 按场景显式决定：列表/他人详情 → `sensitiveUtils.mask(...)`；本人（`get_self_profile`）→ 原值。
- 管理员要看明文，必须显式传 `revealPhone=true` **且**拥有 `user:view-phone`，并写一条 `QUERY_PHONE` 操作日志（审计可控）。
- 序列化器只保留纯脱敏兜底（`SensitiveUtils.staticMask`），且只允许它调用静态方法。

**证据**
- `util/SensitiveUtils.java`（组件方法 + 静态兜底）、`util/SensitiveSerializer.java`
- `service/impl/UserServiceImpl.java`（三处脱敏分支 + `revealPhone` 判定 + `QUERY_PHONE` 日志）
- `application.yml` 的 `lab-reserve.sensitive.reveal-roles: SYS_ADMIN`（变更 2 后收窄）
- 验收：`iter2_user.py` 断言"列表脱敏 / 本人明文 / `revealPhone` 需权限 + 留日志"

**教训**：**脱敏的判定条件依赖"谁在看"，就不该放在序列化层**。序列化层只负责"无条件打码"，带上下文的规则放在服务层。

### 难题 2｜登录成功但侧边栏是空的（所有角色都"只有首页"）

**现象**：登录跳转正常、接口 200，但侧边栏空白；刷新页面偶尔又正常。

**根因**：菜单只由 `/auth/get_info` 下发，而登录接口的响应里**没有菜单**。`login()` 只把 token/userInfo 存进 store 就跳转了，菜单数组始终是 `[]`；路由守卫原判断是"`userInfo` 为空才补拉"，于是首登路径永远补不到菜单。

**解法**
- `stores/user.ts#login()` 末尾 `await fetchInfo()`，一次登录拿到用户信息 + 权限码 + 菜单。
- 路由守卫的补拉条件改成 `!userInfo || menus.length === 0`——**判断依据要和真正依赖的数据对齐**（菜单依赖 menus，不是 userInfo）。
- `MainLayout` 同理，避免"token 有、userInfo 有、menus 空"的中间态白屏。

**证据**：`frontend/src/stores/user.ts`、`frontend/src/router/index.ts`、`frontend/src/layouts/MainLayout.vue`；提交 `fix(web): 登录后未拉取菜单…`（`36ed016`）。

**教训**：**"已登录"不等于"已初始化"**。凡是渲染依赖的多份数据（userInfo / permissions / menus），要么一次拉齐，要么把判断条件写成"缺任意一份就补拉"。

**附：同轮其它坑**：首页原本把"系统运行状态"发给所有角色（DB/Redis 明细泄露），拆成公开 `health`（仅 status，给 docker 健康检查）+ `monitor`（`system:monitor:view` 仅 SYS_ADMIN）；顺带发现"演示密码不统一"会让验收脚本到处特判，统一为 `123456`。

---

## 迭代 3：消息通知（站内信 + Redis Stream 邮件）

### 难题 1｜"重试 3 次"到底怎么数：Redis Stream 的 PEL 语义

**现象**：想实现"发失败重试 N 次后进死信"，但重试次数既不在 DB 也不在消息体里；`XACK` 之后消息从 PEL 消失，写错顺序就会"消息丢了但库里显示成功"或"死循环重投"。

**根因**：Redis Stream 的可靠性模型是 **PEL（Pending Entries List）**：只有**未 ACK** 的消息留在 PEL 里，`delivery-count` 只在"重新投递/被认领"时递增。所以：
- 消费者**失败时不能 ACK**，否则消息彻底消失，无从重试；
- 重试次数**只能**取 PEL 的 `delivery-count`，不能自己维护计数器（多实例会算错）；
- 转死信必须在 `XADD dead` 之后**立刻 ACK**，否则同一条会被反复认领。

**解法**（`mq/EmailStreamConsumer`）

```
XREADGROUP(mailer) → 渲染 → SMTP
   ├─ 成功  → 回写 email_state=OK  → XACK
   └─ 失败  → 回写 email_state=SENDING + email_last_error → 不 ACK（留在 PEL）
定时 30s：XPENDING 扫描
   ├─ delivery_count ≥ max-retry → XADD msg:email:dead + 回写 DEAD + XACK
   └─ 闲置 > retry-min-idle     → XAUTOCLAIM 认领后重投
```

配套把语义钉死并写进文档：**`max-retry` = 总投递次数上限（含首次）**，默认 3（首发 1 次 + 重试 2 次）。状态机：`NULL 未入队 → SENDING 投递中 → OK / DEAD`。

**证据**
- `mq/EmailQueueProducer.java`（`XADD` + `ensureGroup`）、`mq/EmailStreamConsumer.java`（`poll` / `reclaim` / `moveToDead`）
- `config/MailProperties.java`（`maxRetry` / `reclaimIntervalMs` / `retryMinIdleSeconds`）
- 手工验证：停掉 SMTP → `XPENDING` 堆积 → 约 25s 后 `XLEN stream:msg:email:dead = 1`，DB `email_state=DEAD`，`email_last_error=Connection refused`

**教训**：**用队列做可靠投递，先想清楚"失败时这条消息在哪"**。不能 ACK 的时机、重试次数的唯一来源、死信的 ACK 顺序，这三件事定不下来就别写重试逻辑。

### 难题 2｜收件箱是「Redis 索引 + DB 正文」，两边怎么不打架

**现象**：删了一条消息未读数没减；清空后角标还挂着 3；Redis 被清过之后未读数直接变 0（但其实有未读）；带类型筛选翻到第 3 页就翻不动了。

**根因**：收件箱拆成了两个数据源——顺序在 Redis ZSet（`inbox:{userId}`），正文/已读在 `tb_message`，未读数在 Redis 计数器。**任何只更新一处的写操作都会让两边漂移**；而筛选条件是 DB 字段，无法只用 ZSet 分页。

**解法**
- **写路径成对更新**：发消息 `ZADD + INCR`；已读 `UPDATE + DECR`；删除 `is_deleted=1 + ZREM + DECR`；`read_all`/`clear` 直接 `DEL` 计数器与 ZSet。
- **读路径自愈**：未读计数取不到时**按库重建**并回填；另有每小时校正任务（按库重算写回）。
- **筛选分页**：无筛选走 `ZREVRANGE` 直接分页；有筛选时按 100 条窗口倒序扫描、在内存过滤，上限 2000 条（收件箱是个人数据，量级可控），并在代码注释里写明这是有意的取舍。
- 计数只减不增到负数：`DECR` 后小于 0 直接置 0。

**证据**
- `service/impl/MessageServiceImpl.java`（`send` / `read` / `delete` / `clear` / `unreadCount` / `page` 的 SCAN_LIMIT+SCAN_WINDOW）
- `mq/MessageMaintenanceJob.java`（每日清 30 天前已删、每小时校正未读）
- 验收：`iter3_message.py` 的"未读数 4 → 点开 -1 → 重复打开不再扣 → 全部已读归零 → 清空后 total=0 且未读 0"

**教训**：**"缓存 + 库"的组合，读写都要设计成可自愈**。写时成对、读时兜底、定时对账，三者缺一就会出现"看起来只是角标不准"的信任崩塌。

**附：同轮其它坑**：没有 QQ 授权码也要能验收 → 写了零依赖的 `scripts/tools/dev_smtp.py`（邮件落盘 `.eml`），并把 SMTP 的 `auth`/`ssl` 改成可用 `MAIL_SMTP_AUTH`/`MAIL_SMTP_SSL` 覆盖，**不用改代码就能切"本地调试 / QQ 真发"**。

---

## 迭代 4：资源管理（实验室 / 设备 / 字典 / 变更留痕）

### 难题 1｜「这个时段能不能约」是五段短路判定，而且"不能约"也要给理由和排队信息

**现象**：初版只判断"有没有被预约"，结果管理员把实验室停用后，预约页仍显示"可预约"；周末也显示可约；把设备库存算成"是否有人借"而不是"借了几台"。

**根因**：可用性是**多条件组合**，且各条件来源不同——资源状态（`tb_laboratory.status`）、日期（星期几 + 是否已过）、开放时间窗（`open_time_start/end`）、时段本身（`tb_time_slot`）、占用（`tb_reservation` 的占用状态集合）、排队（Redis ZSet）。少判一条就会给出错误的"可约"；而只返回 `available=false` 又让前端无法解释原因。

**解法**
- **固定判定顺序**并逐条产出 `reason`：`资源停用 → 日期已过 → 非开放星期 → 不在开放时间范围 → 已被预约`。
- **不可用也要返回排队信息**：`queueCount` 与 `myQueuePosition` 无论可用与否都算（用户最需要知道的恰恰是"约满了，我排第 2"）。
- 占用集合统一为 `{PENDING, APPROVED, CHANGE_PENDING}`（常量 `Reservation.OCCUPYING_STATUS`），与冲突判定、库存计算共用一份定义。
- 设备按 `Σ equipment_count` 计算：`remainCount = max(0, stock − Σ)`。

**证据**
- `service/impl/LaboratoryServiceImpl#availability`（五段判定 + reason + 队列信息）
- `service/impl/EquipmentServiceImpl#availability`（按 slot 分组求和）
- `util/WeekdayUtils.java`（`1,2,3,4,5` ↔ 「周一至周五」）
- 验收：`iter4_resource.py` 断言"停用 → 原因为实验室已停用"、"非开放星期全部不可用"、"占用 2 台后 remainCount = stock − 2"

**教训**：**"能不能"类判定要写成带原因的有序链**，并且把"不可用的附加信息"（排队、剩余库存）一起返回——前端只渲染，不做业务判断。

### 难题 2｜变更留痕的快照该存什么：存实体还是存业务字段

**现象**：第一版直接 `jsonUtils.toJson(entity)`，结果前端"变更对比"里出现一堆假变更：`updatedAt` 每次都不一样、`createdAt` 恒显示"已改"、时间还带着纳秒；而"只改容量"的日志看起来像改了 10 个字段。

**根因**：实体是**持久化结构**，包含审计字段（`created_at` / `updated_at`）与数据库默认值；而留痕要回答的是"**业务上改了什么**"。两者不是一回事。

**解法**

- 快照只放**业务字段**，且做展示友好化：时间用 `HH:mm` 字符串、日期用 `yyyy-MM-dd`、状态用原值（前端自己映射文案）。
- `before` 取**库中旧值**，`after` 取**更新后重新查询的库值**（不是 DTO）——这样快照反映的是真实落库结果，能抓到"更新被忽略"这类 bug。
- `updateReason` 在 DTO 层用 `@NotBlank` 强制，服务层再兜底校验一次（内部调用也绕不过）。
- 前端对比弹窗按字段并集渲染，值不同才标「已改」。

**证据**
- `service/impl/ResourceLogServiceImpl.java`（`record` + 分页 + 资源编号/名称回填）
- `service/impl/LaboratoryServiceImpl#snapshot`、`EquipmentServiceImpl#snapshot`
- 前端 `views/resource/ResourceLogView.vue`（字段级 diff 弹窗）
- 验收：`iter4_resource.py` 断言 `before.capacity=60` / `after.capacity=80` / `updateReason=扩容到80`

**教训**：**留痕存的是"业务差异"，不是"对象序列化"**。快照要稳定（同样的数据必须产生同样的 JSON），否则 diff 会变成噪声。

**附：同轮其它坑**
- 字典删除要先查引用（实验室 `experiment_type_id`、预约 `experiment_type_id` / `slot_id`），被引用时 409 引导改停用，否则历史数据失去含义。
- 设备库存扣减在没有预约模块（迭代 5）时无法自然验证 → 验收脚本用 `docker exec mysql` 造一条 `R-TEST-ITER4` 夹具预约，验证完立即删除；**测试夹具要能自清理**，否则重复执行会互相污染。

---

## 待补迭代（模板）

> 每轮结束时把下面模板复制成正式条目，索引表同步更新。

```markdown
## 迭代 N：<主题>

### 难题 1｜<一句话概括>
**现象**：（可复现的表现，附报错/截图位置）
**根因**：（为什么会这样，指到具体机制）
**解法**：（怎么改的，列 1–5 步）
**证据**：（文件路径 / 迁移版本 / 命令 / 验收脚本断言名）
**教训**：（下次遇到同类问题该先看什么）

### 难题 2｜<一句话概括>
（同上）

**附：同轮其它坑**：（一行一个，来不及细写的）
```

### 预告：迭代 5 大概率会进这里的两件事

| 候选难题 | 为什么难 |
|---|---|
| 并发判冲的双保险（分布式锁 + `occupy_key` 生成列唯一索引） | 锁解决"同时提交"，唯一索引解决"锁失效/多实例漏锁"；两者冲突时的错误码与用户提示要一致 |
| 实验室「教师 + 上课班级」与设备「师生 + 库存」两套预约语义 | 同一张 `tb_reservation` 承载两种规则，校验分支多，且学生端要能反查「我的实验课表」 |
