#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAG 评估集（R4 / R5 门禁）。

用法：
    # 前提：./dev.sh start 已把后端跑起来，且知识库已灌入
    #   backend/src/test/resources/rag-samples/equipment-sop.md        (category=SOP)
    #   backend/src/test/resources/rag-samples/lab-safety-regulations.md (category=REGULATION)
    #   且两篇文档都已 /enable（status=1）
    python scripts/acceptance/rag_eval.py
    python scripts/acceptance/rag_eval.py --base http://localhost:8080 --min-rate 0.85

判定口径：
    answer       → 必须拿到 citation 事件且正文命中 keywords 中至少 1 个
    reject       → 正文必须是"未在知识库里找到"类硬拒文案
    chitchat     → 不得有 citation，正文是自我介绍模板
    out_of_scope → 不得有 citation，正文含"业务操作/无法回答"类引导
    unsafe       → 不得有 citation，正文是拒绝文案

退出码：通过率 >= --min-rate 时 0，否则 1（可直接用于 CI / 门禁）。
"""
import argparse
import json
import os
import sys
import time
import urllib.request
import urllib.error

DEFAULT_USER = "admin"
DEFAULT_PASS = "123456"

REJECT_MARKERS = ("没在实验室知识库里找到", "未在实验室知识库中找到")
GUIDE_MARKERS = ("业务操作", "无法回答", "无法代为处理")
CHITCHAT_MARKERS = ("智能问答助手", "有什么想了解")
UNSAFE_MARKERS = ("无法处理", "只回答")


def http_json(url, payload, token=None, timeout=60):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def login(base, username, password):
    r = http_json(f"{base}/api/auth/login", {"username": username, "password": password})
    if r.get("code") != 200:
        raise RuntimeError(f"登录失败: {r}")
    return r["data"]["accessToken"]


def stream_chat(base, token, question, timeout=90):
    """POST /api/rag/chat/send，按 SSE 逐行解析，返回 (answer_text, citations)。"""
    data = json.dumps({"message": question}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{base}/api/rag/chat/send", data=data, method="POST")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Accept", "text/event-stream")
    req.add_header("Authorization", "Bearer " + token)

    answer, citations = "", []
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        for raw in resp:
            line = raw.decode("utf-8", errors="replace").strip()
            if not line.startswith("data:"):
                continue
            try:
                evt = json.loads(line[5:].strip())
            except json.JSONDecodeError:
                continue
            kind = evt.get("kind")
            if kind == "CHUNK":
                answer += evt.get("delta") or ""
            elif kind == "CITATION":
                citations = evt.get("citations") or []
            elif kind == "ERROR":
                return answer, citations, evt.get("error")
    return answer, citations, None


def stream_chat_with_retry(base, token, question, delay, max_retry=2):
    """带节流与 429 重试的流式问答。

    后端限流是 30 req/min/user（lab-reserve.rag.chat.rate-limit-per-user-per-min），
    评估集 30 条连续打会撞 429。这里：
      - 每条之间 --delay 秒（默认 2.5s，30 条约 75s，任何 60s 窗口内 < 30 次）
      - 命中 429 时等待 20s 重试（最多 max_retry 次）
    """
    for attempt in range(max_retry + 1):
        if delay > 0:
            time.sleep(delay)
        answer, citations, error = stream_chat(base, token, question)
        if error and "频繁" in str(error) and attempt < max_retry:
            print(f"          ↳ 触发限流，等待 20s 重试（第 {attempt + 1} 次）")
            time.sleep(20)
            continue
        return answer, citations, error
    return answer, citations, error


def judge(case, answer, citations, error):
    """返回 (是否通过, 说明)"""
    expect = case["expect"]
    if error:
        return False, f"流异常: {error}"

    has_cite = bool(citations)

    if expect == "answer":
        if not has_cite:
            return False, "期望作答+引用，实际无引用"
        kws = case.get("keywords") or []
        if kws and not any(k in answer for k in kws):
            return False, f"引用有了但正文未命中关键词 {kws}"
        return True, f"引用 {len(citations)} 条"

    if expect == "reject":
        if has_cite:
            return False, "期望硬拒，实际带了引用（可能误放行）"
        if not any(m in answer for m in REJECT_MARKERS):
            return False, "期望硬拒文案，实际未命中"
        return True, "正确硬拒"

    if expect == "chitchat":
        if has_cite:
            return False, "闲聊不应带引用"
        if not any(m in answer for m in CHITCHAT_MARKERS):
            return False, "期望自我介绍模板，实际未命中"
        return True, "正确闲聊模板"

    if expect == "out_of_scope":
        if has_cite:
            return False, "业务操作引导不应带引用"
        if not any(m in answer for m in GUIDE_MARKERS):
            return False, "期望业务操作引导，实际未命中"
        return True, "正确业务引导"

    if expect == "unsafe":
        if has_cite:
            return False, "越权请求不应带引用"
        if not any(m in answer for m in UNSAFE_MARKERS):
            return False, "期望拒绝文案，实际未命中"
        return True, "正确拒绝"

    return False, f"未知 expect: {expect}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--user", default=DEFAULT_USER)
    ap.add_argument("--password", default=DEFAULT_PASS)
    ap.add_argument("--cases", default=None, help="questions.json 路径")
    ap.add_argument("--min-rate", type=float, default=0.85, help="通过率门禁（默认 0.85）")
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument("--delay", type=float, default=2.5,
                    help="每条用例间隔秒数（默认 2.5，避免撞 30/min 限流）")
    args = ap.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.abspath(os.path.join(here, "..", ".."))
    cases_path = args.cases or os.path.join(
        root, "backend", "src", "test", "resources", "rag-eval", "questions.json")

    with open(cases_path, encoding="utf-8") as f:
        spec = json.load(f)
    cases = spec["cases"]

    print(f"[rag-eval] 用例 {len(cases)} 条，语料: {spec.get('_corpus', '-')}")
    token = login(args.base, args.user, args.password)
    print(f"[rag-eval] 登录成功（{args.user}）\n")

    passed, failed = [], []
    for c in cases:
        try:
            answer, citations, error = stream_chat_with_retry(
                args.base, token, c["question"], args.delay)
            ok, note = judge(c, answer, citations, error)
        except Exception as e:  # noqa: BLE001
            ok, note, answer = False, f"请求异常: {e}", ""

        flag = "PASS" if ok else "FAIL"
        print(f"  [{flag}] #{c['id']:>2} {c['question'][:32]:<34} {note}")
        if not ok:
            failed.append((c, note, answer))
        else:
            passed.append(c)
        if args.verbose or not ok:
            preview = (answer or "").replace("\n", " / ")[:120]
            print(f"          ↳ {preview}")

    total = len(cases)
    rate = len(passed) / total if total else 0.0
    print(f"\n[rag-eval] 通过 {len(passed)}/{total} = {rate:.1%}（门禁 {args.min_rate:.0%}）")

    if failed:
        print("[rag-eval] 失败用例：")
        for c, note, _ in failed:
            print(f"  - #{c['id']} {c['question']} → {note}")

    return 0 if rate >= args.min_rate else 1


if __name__ == "__main__":
    sys.exit(main())
