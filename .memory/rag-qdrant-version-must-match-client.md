---
name: rag-qdrant-version-must-match-client
description: Qdrant 服务端版本必须与 pom 里的 io.qdrant:client 版本对齐，否则 dense vector 序列化不兼容
type: project-gotcha
---

**现象**：`io.qdrant:client:1.19.0` + `qdrant/qdrant:v1.12.0` 组合下，upsert 报
`INVALID_ARGUMENT: Wrong input: Vector dimension error: expected dim: 1024, got 0`。
SDK 只打 WARN 不报错，症状是"向量维度为 0"。

**Why**：1.19 客户端把 dense 向量序列化为 `Vector{dense: DenseVector{data:[...]}}`，
1.12 服务端读的是旧结构 `Vector{data:[...]}` → 服务端看到 0 个元素。
SDK 版本兼容检查只 WARN（minor 差 ≤1 才允许），实际序列化已经跨代不兼容。

**How to apply**：
- 改 `io.qdrant:client` 版本时，**同步改 `docker-compose.yml` 里的 `qdrant/qdrant:vX.Y.Z`**；
  反过来升服务端时，也要确认 pom 的 client 版本跟着走。
- 升级后需 `docker compose down -v && docker compose up -d`（存储格式跨大版本可能不兼容），
  注意这会**同时清掉 MySQL 卷**，种子数据由 DataSeeder 重灌。
- 当前锁定：`io.qdrant:client:1.19.0` + `qdrant/qdrant:v1.19.0`。

**Related**：[[rag-no-tool-calling-scope]]