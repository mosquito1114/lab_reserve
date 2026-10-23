---
name: rag-three-stage-relevance-gate
description: RAG 相关性判定不能用单一 cosine 阈值（小语料下正负样本分数重叠），必须三段式：高分放行/低分拒/灰区 LLM 判
type: project-gotcha
---

**现象**：R4 引入 Hybrid 检索后，负样本「量子计算机的低温制冷原理是什么」
被当作命中并开始生成答案（应该硬拒）。

**Why**：实测 `text-embedding-v3` 的 cosine 分布（KB 仅 2 chunk）：
```
正样本 离心机使用前要检查什么      0.7121
正样本 危险化学品五双管理是什么    0.4994
负样本 量子计算机的低温制冷原理    0.4638   ← 夹在正样本中间
正样本 易燃易爆化学品怎么存放      0.4508
```
负样本分数**落在正样本区间内部**。任何单一阈值都无法分开：
0.45 会漏真阳性（0.4508 那条），0.40 会放行假阳性（0.4638）。
这是小语料的固有局限——不相关的中文句子在嵌入空间里也靠得很近。

另外 **RRF 分不能用来做阈值判断**：RRF 是排名分 `1/(60+rank)`，量级 ~0.016~0.033，
即使库里完全没有相关内容，也会有东西排第一拿分。RRF 只负责排序。

**How to apply**：三段式门禁（Corrective RAG 做法），见 `service/RelevanceJudge`
```
dense >= kb-accept-score (0.60)  → 直接放行（明确相关，省一次 LLM）
dense <  kb-reject-score (0.35)  → 直接拒  （明确不相关，省一次 LLM）
中间灰区                          → cheapLlm 判「证据是否足以回答」，YES/NO
                                    LLM 异常时保守拒答
```
- 阈值判断用 **dense cosine**（绝对量），RRF 只排序
- 判官只做 Yes/No 二分类、不产生正文，判 NO 后仍走硬拒 —— 与"零幻觉"原则一致
- 实测 6 条样本只有 3 条进灰区，额外 LLM 成本可控
- KB 扩容（几十篇以上）后 cosine 区分度会改善，可上调 `kb-reject-score`

**Related**：[[rag-evidence-must-be-full-chunk-not-snippet]]
