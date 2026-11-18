#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG 多轮上下文记忆评估（R6）。

与 rag_eval.py（单轮）互补：这里每个用例是**一串**提问，判定作用于最后一轮。

用法：
    python scripts/acceptance/rag_multiturn_eval.py
    python scripts/acceptance/rag_multiturn_eval.py --delay 3

判定类型：
    answer  → 最后一轮必须作答且带引用，命中关键词
    recall  → 上下文回显：不应带引用（未检索知识库），正文应复述历史
    unsafe  → 必须拒绝，且不带引用
"""
import argparse
import json
import os
import sys
import time
import urllib.request

DEFAULT_USER = "admin"
DEFAULT_PASS = "123456"

UNSAFE_MARKERS = ["无法处理", "无法回答", "不能提供", "抱歉"]
REJECT_MARKERS = ["没有找到", "没有相关内容", "没在", "无法回答"]


def http_json(url, payload, token=None, timeout=60):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Content-Type": "application/json; charset=utf-8",
            **({"Authorization": "Bearer " + token} if token else {}),
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def login(base, username, password):
    r = http_json(base + "/api/auth/login", {"username": username, "password": password})
    return r["data"]["accessToken"]


def stream_chat(base, token, question, session_id=None, timeout=120):
    """发起一次流式问答，返回 (answer, citations, session_id, error)。

    session_id 为 None → 新建会话；否则续聊该会话。
    """
    payload = {"message": question}
    if session_id:
        payload["sessionId"] = session_id
    req = urllib.request.Request(
        base + "/api/rag/chat/send",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Authorization": "Bearer " + token,
        },
        method="POST",
    )
    answer, citations, sid, error = "", [], session_id, None
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            for raw in resp:
                line = raw.decode("utf-8", "replace").strip()
                if not line.startswith("data:"):
                    continue
                try:
                    evt = json.loads(line[5:].strip())
                except Exception:
                    continue
                kind = evt.get("kind")
                if kind == "CHUNK":
                    answer += evt.get("delta") or ""
                elif kind == "CITATION":
                    citations = evt.get("citations") or []
                elif kind == "ERROR":
                    error = evt.get("error")
                if evt.get("sessionId"):
                    sid = evt["sessionId"]
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        try:
            error = json.loads(body).get("message") or body[:120]
        except Exception:
            error = body[:120]
    return answer, citations, sid, error


def judge(case, answer, citations, error):
    """判定最后一轮是否达标，返回 (是否通过, 说明)。"""
    if error:
        return False, f"流异常: {error}"

    expect = case["expect"]
    n = len(citations)
    kws = case.get("keywords") or []
    min_c = case.get("min_citations")
    max_c = case.get("max_citations")

    if min_c is not None and n < min_c:
        return False, f"引用数 {n} < 期望下限 {min_c}"
    if max_c is not None and n > max_c:
        return False, f"引用数 {n} > 期望上限 {max_c}（不应带引用）"

    if expect == "answer":
        if not n:
            return False, "期望作答+引用，实际无引用"
        if kws and not any(k in answer for k in kws):
            return False, f"正文未命中关键词 {kws}"
        return True, f"作答+引用 {n} 条"

    if expect == "recall":
        if not kws:
            return True, f"上下文回显（引用 {n} 条）"
        if not any(k in answer for k in kws):
            return False, f"回显未命中关键词 {kws}"
        return True, f"上下文回显（引用 {n} 条）"

    if expect == "unsafe":
        if n:
            return False, "越权请求不应带引用"
        if not any(m in answer for m in UNSAFE_MARKERS):
            return False, "期望拒绝文案，实际未命中"
        return True, "正确拒绝"

    if expect == "reject":
        if n:
            return False, "期望硬拒，实际带了引用"
        if not any(m in answer for m in REJECT_MARKERS):
            return False, "期望硬拒文案，实际未命中"
        return True, "正确硬拒"

    return False, f"未知 expect: {expect}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--user", default=DEFAULT_USER)
    ap.add_argument("--password", default=DEFAULT_PASS)
    ap.add_argument("--cases", default=None, help="multiturn.json 路径")
    ap.add_argument("--min-rate", type=float, default=0.85, help="通过率门禁")
    ap.add_argument("--delay", type=float, default=3.0, help="每轮间隔秒数（避开 30/min 限流）")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.abspath(os.path.join(here, "..", ".."))
    cases_path = args.cases or os.path.join(
        root, "backend", "src", "test", "resources", "rag-eval", "multiturn.json")

    with open(cases_path, encoding="utf-8") as f:
        spec = json.load(f)
    cases = spec["cases"]

    print(f"[rag-mt-eval] 多轮用例 {len(cases)} 组")
    token = login(args.base, args.user, args.password)
    print(f"[rag-mt-eval] 登录成功（{args.user}）\n")

    passed, failed = [], []
    for c in cases:
        sid = None
        answer, citations, error = "", [], None
        try:
            for i, q in enumerate(c["turns"]):
                if i > 0 and args.delay > 0:
                    time.sleep(args.delay)
                answer, citations, sid, error = stream_chat(
                    args.base, token, q, sid)
                if error:
                    break
            ok, note = judge(c, answer, citations, error)
        except Exception as e:  # noqa: BLE001
            ok, note, answer = False, f"请求异常: {e}", ""

        flag = "PASS" if ok else "FAIL"
        print(f"  [{flag}] #{c['id']:>2} {c['name']:<26} {note}")
        if not ok:
            failed.append((c, note, answer))
            preview = (answer or "").replace("\n", " / ")[:150]
            print(f"          ↳ {preview}")
        else:
            passed.append(c)
        if args.verbose:
            for i, q in enumerate(c["turns"]):
                print(f"          轮{i + 1}: {q}")
            print(f"          ↳ {(answer or '').replace(chr(10), ' / ')[:200]}")

    total = len(cases)
    rate = len(passed) / total if total else 0.0
    print(f"\n[rag-mt-eval] 通过 {len(passed)}/{total} = {rate:.1%}（门禁 {args.min_rate:.0%}）")

    if failed:
        print("\n失败明细：")
        for c, note, ans in failed:
            print(f"  #{c['id']} {c['name']}: {note}")
            print(f"     {c.get('desc', '')}")
            print(f"     答案: {(ans or '')[:200]}")

    ok = rate >= args.min_rate
    print(f"[rag-mt-eval] 门禁判定: {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
