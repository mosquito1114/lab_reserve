---
name: rag-spring-ai-openai-embedding-incompatible-with-dashscope
description: Spring AI 1.0 GA 的 OpenAiEmbeddingModel 读不了 DashScope 兼容模式的 embedding 响应，需自写 HTTP 客户端
type: project-gotcha
---

**现象**：`spring-ai-starter-model-openai:1.0.0` 的 `OpenAiEmbeddingModel` 调
DashScope `https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings` 时抛：
```
RestClientException: Error while extracting response for type
[OpenAiApi$EmbeddingList<OpenAiApi$Embedding>] and content type [application/octet-stream]
```
但 curl 同一个接口实际返回 `content-type: application/json`。

**Why**：Spring 的 RestClient 按响应 Content-Type 选 MessageConverter；DashScope 在
某些 embedding 请求形态下返回的 content-type 不被 Spring 识别为 JSON，于是走了
octet-stream 分支，反序列化直接失败。Chat 侧（`/chat/completions`）没这个问题。

**How to apply**：
- Embedding 走自写的 `com.labreserve.rag.adapter.DashScopeEmbeddingClient`
  （JDK `HttpClient` + Jackson，显式 `Accept: application/json`）。
- Chat 侧继续用 Spring AI 的 `ChatClient` / `ChatModel`（R3 起）。
- 若以后换 provider 要重新验证这条。

**Related**：[[rag-no-tool-calling-scope]]