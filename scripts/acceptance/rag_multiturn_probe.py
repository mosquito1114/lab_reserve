#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG 多轮对话手工验证脚本（R6 上下文记忆）。

用法：
    python scripts/acceptance/rag_multiturn_probe.py

它会建一个会话，按顺序问若干轮，打印每轮回答与引用。
判定"是否有记忆"的关键在后面几轮：
  - 「你刚才说的再详细点」     → 应基于上一轮内容继续答
  - 「刚才提到的第 2 条是什么」 → 应能指回上一轮列表里的第 2 项
  - 「总结一下我们前面讨论的」 → 应能概括本会话历史
"""
import json
import sys
import time
import urllib.request

BASE = "http://localhost:8080"
USER = "admin"
PWD = "123456"

TURNS = [
    "离心机使用前要检查什么",
    "你刚才说的再详细点",
    "刚才提到的第 2 条是什么",
    "我一开始问的是什么",
]


def post(path, payload, token=None, raw=False):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Content-Type": "application/json; charset=utf-8",
            **({"Authorization": "Bearer " + token} if token else {}),
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        body = resp.read().decode("utf-8")
    return body if raw else json.loads(body)


def ask(token, message, session_id=None):
    payload = {"message": message}
    if session_id:
        payload["sessionId"] = session_id
    raw = post("/api/rag/chat/send", payload, token, raw=True)
    answer, citations, sid = "", [], None
    for line in raw.split("\n"):
        if not line.startswith("data:"):
            continue
        try:
            e = json.loads(line[5:].strip())
        except Exception:
            continue
        if e.get("kind") == "CHUNK":
            answer += e.get("delta") or ""
        if e.get("kind") == "CITATION":
            citations = e.get("citations") or []
        if e.get("sessionId"):
            sid = e["sessionId"]
    return answer, citations, sid


def main():
    tok = post("/api/auth/login", {"username": USER, "password": PWD})["data"]["accessToken"]
    print("登录成功，开始多轮对话：\n")

    sid = None
    for i, q in enumerate(TURNS, 1):
        print(f"【第 {i} 轮】{q}")
        ans, cits, new_sid = ask(tok, q, sid)
        sid = new_sid or sid
        flat = ans.replace("\n", " ")
        print(f"  → {flat[:220]}")
        print(f"  [引用 {len(cits)} 条 | sessionId={sid}]\n")
        if i < len(TURNS):
            time.sleep(3)

    print("=" * 60)
    print("判读要点：")
    print("  第 2 轮「再详细点」若答的还是同一批条目但不更细 → 记忆仅停留在检索重写层")
    print("  第 3 轮「第 2 条是什么」若能指回具体条目   → 记忆真的进了 prompt")
    print("  第 4 轮「我一开始问的是什么」若答不出       → 早期上下文没进 prompt")
    print("=" * 60)


if __name__ == "__main__":
    sys.exit(main())
