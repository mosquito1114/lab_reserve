---
name: rag-permissions-must-live-in-seed-data-not-flyway
description: RAG 权限分配必须在 seed_data.sql 里，不能只靠 Flyway 迁移里的 INSERT...SELECT FROM tb_role
type: project-gotcha
---

**现象**：`docker compose down -v` 全量重建库后，admin 调 `/api/rag/admin/upload` 返回 403，
但 `tb_permission` 里 6 个 `rag:*` 权限码都在、`tb_role_permission` 里一条对应关系都没有。

**Why**：Flyway 迁移跑在 `DataSeeder` **之前**。V2/V3 迁移里的
`INSERT IGNORE INTO tb_role_permission SELECT r.id, p.id FROM tb_role r JOIN tb_permission p ...`
执行时 `tb_role` 表刚被 V1 建出来、**一行数据都还没有** → JOIN 结果为空 → 静默插入 0 行，
`INSERT IGNORE` 不报错，所以历史上一直没被发现。

**How to apply**：
- 任何"按角色 code 分配权限"的语句，必须同时出现在 `backend/src/main/resources/data/seed_data.sql`
  的权限矩阵区（此时角色已由种子脚本灌入），用 `INSERT IGNORE` 保证幂等。
- Flyway 迁移里保留一份用于兼容"已有库"场景，但不能只靠它。
- 修完记得清 Redis 权限缓存：`lab:reserve:perm:role:{roleId}`。

**Related**：[[rag-no-tool-calling-scope]]