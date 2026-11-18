---
name: rag-token-usage-needs-chatresponse-not-content
description: RAG 的 token 统计必须用 chatResponse() 而不是 content()，否则 usage 被静默丢弃
type: project-gotcha
---

**现象**：`tb_rag_usage_stats.used_tokens` 恒为 0，用量报表完全不可信。

**Why**：三层叠加，每一层单独看都"没问题"：
1. `DashScopeChatModel.stream()` 只解析 `choices[0].delta.content`，
   **响应里的 `usage` 字段根本没读**；且请求没带 `stream_options.include_usage`，
   provider 根本不会在流里给 usage
2. `AnswerSynthesizer` 用 `ChatClient...stream().content()` →
   `Flux<String>`，**ChatResponse 的 metadata（含 usage）被彻底丢弃**
3. `DashScopeChatModel.call()` 同样只 `new ChatResponse(List.of(generation))`，
   没传 metadata

**How to apply**：
- 要拿 token 必须用 `.chatResponse()`（流式）/ `.call().chatResponse()`（非流式），
  **不能用 `.content()`**
- `DashScopeChatModel` 里 `ChatResponseMetadata.builder().usage(new DefaultUsage(p,c,t)).build()`
- **usage 字段有两种方言**，必须同时兼容：
  - OpenAI 兼容：`prompt_tokens` / `completion_tokens` / `total_tokens`
  - DashScope 原生：`input_tokens` / `output_tokens` / `total_tokens`
- 流式里 usage 在**最后一个 usage-only chunk**（`choices: []` + `usage`），
  要单独 emit 一个带 metadata 的 ChatResponse
- **Spring AI 没有 usage 时返回 `EmptyUsage` 对象而不是 null** ——
  判空要判"三个 token 字段是否都为 null"，不能判 `getUsage() == null`
- provider 不返回 usage 时要**本地估算兜底**（中文 1 字 ≈ 1 token），
  否则统计仍是 0，白改

**离线可测**：用 JDK 内置 `com.sun.net.httpserver.HttpServer` 起假服务，
不依赖真实 LLM 就能覆盖两种方言 + usage-only chunk + 请求参数断言。

**Related**：[[rag-context-memory-must-reach-the-llm-prompt]]
