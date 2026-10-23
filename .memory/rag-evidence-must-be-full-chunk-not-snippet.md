---
name: rag-evidence-must-be-full-chunk-not-snippet
description: 给 LLM 的证据必须是完整 chunk 正文，给 UI 的才是截断 snippet；混用会导致"检索对了却答不出来"
type: project-gotcha
---

**现象**：R4 评估集首轮 11/20（55%），失败用例症状高度一致——
SSE 里 `CITATION` 事件有引用（说明检索正确命中），但正文回答"知识库里没有相关内容"。

**Why**：`KbRetriever` 把 `CitationVO.snippet`（为 UI 截断的 **200 字**）直接喂给
`RelevanceJudge` 和 `AnswerSynthesizer`。而 chunk 0 有 591 字，
「易燃易爆化学品怎么存放」的答案在 chunk 第 2 章（200 字开外），
LLM 只看到 chunk 开头（"# 高速离心机 SOP / ## 第一章 适用范围…"），
自然判为"不相关 / 没有该内容"。

**How to apply**：
- 分成两套数据：
  - `CitationVO.snippet` → 只给 UI 展示与角标（200 字够用）
  - `RetrievalResult.evidenceTexts`（完整 `RagChunk.content`）→ 只给 LLM
- `KbRetriever.retrieve()` 返回 `RetrievalResult{citations, evidenceTexts}`，
  `AnswerSynthesizer.stream(question, citations, evidenceTexts)`、
  `RelevanceJudge.judge(question, denseTop1, evidenceTexts, citations)`
- 单片正文给 LLM 前限 2000 字（正常 chunk ≤ 750 字，不会触发）
- 修复后评估 20/20（100%）

**这个坑在 R4 期间踩了两次**（先判官、后合成器）。新增任何"把检索结果喂 LLM"的环节，
第一件事就是确认用的是完整正文而不是 snippet。

**Related**：[[rag-three-stage-relevance-gate]]
