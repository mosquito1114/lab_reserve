---
name: rag-no-tool-calling-scope
description: lab_reserve RAG 模块的用户问答侧只做知识库问答，不提供 Tool Calling / MCP / 业务接口透传
type: project-decision
---

**决策（2026-10-15 用户明确）**：lab_reserve 的 RAG 模块只做**知识库问答**（RAG retrieval + 带引用作答）。用户问答侧**不**提供：
- Tool Calling（不再实现 `ToolOrchestrator` / `@Tool` / `MethodToolCallback` / `RagToolRegistry`）
- MCP（不接 Model Context Protocol）
- 业务接口透传（不对 `ReservationService` / `RepairService` 等加 `xxxForRag(...)` 适配方法）

**Why**：用户判断课设/项目目标是"专注做好 RAG 知识库问答"，业务操作类问题由原有 7 个业务模块的前端页面负责，AI 不介入写操作。Tool Calling 会显著扩大越权/幻觉风险面，且与"业务模块未实现"的现状不匹配。

**How to apply**：
- `docs/rag-design.md` v2.1 §四 Step 5 已删除；QUERY 链变为 6 步：
  Gate → ContextLoader → QueryRewriter → IntentRouter(3 维) → KbRetriever → AnswerSynthesizer → Persist
- `IntentRouter` 的 `biz` 维度改为 `out_of_scope`（问"我的预约/怎么预约"等业务操作 → 模板引导去业务页面，不检索不调 LLM）
- 检索未命中 → **硬拒，不调 LLM**（零幻觉）
- `tb_rag_message.tool_calls` / `tb_rag_usage_stats.tool_calls` 两列**保留为 reserved，永远写 NULL**（不做 V7 drop）
- prompt injection 防护只剩 3 道墙（系统 Prompt 约束 / 输出 Schema 强约束 / SensitiveUtils 后置脱敏），删掉原第 2 道墙"Tool 签名物理隔离"
- INGEST 链（EXTRACT→SANITIZE→CHUNK→EMBED→UPSERT→FINALIZE）**不受此决策影响**

**Related**：[[rag-qdrant-version-must-match-client]]