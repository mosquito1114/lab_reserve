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
| 存储 | MySQL 8（24 张表，统一 `tb_` 前缀）· Redis 7（排队 ZSet / 收件箱 / 邮件 Stream / 分布式锁） |
| 部署 | Docker Compose（`docker-compose.yml`） |

```
backend/src/main/java/com/labreserve/
├─ common/   annotation(@OpLog/@Sensitive) · aop · constant · enums · exception · result
├─ config/   MP / Redis / Redisson / Jackson / Knife4j / Async / Security / Properties
├─ security/ JWT（Provider/Filter）· PermissionService · @RequirePerm + 切面 · MenuService
├─ util/     工具组件（RedisUtils / RedisLockTemplate / IpUtils / JsonUtils / SensitiveUtils …）
├─ model/    entity · dto · vo
├─ controller/ · service/ · service/impl/ · mapper/
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
cd frontend && npm run dev                # 5173，/api 由 vite proxy 转发到 8080
```

| 用途 | 地址 |
|---|---|
| 前端（dev） | http://localhost:5173 |
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
