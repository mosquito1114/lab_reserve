#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""预约并发压测：对同一实验室 / 同一时段并发提交 N 次，验证「恰好 1 条成功」。

用法：
    python3 scripts/stress/reservation_concurrency.py            # 默认 50 并发
    python3 scripts/stress/reservation_concurrency.py -n 100     # 自定义并发数
    python3 scripts/stress/reservation_concurrency.py --keep     # 保留压测产生的预约（默认清理）

前置：./dev.sh start，且数据库里有教师 / 班级 / 实验室数据（种子已含）。

判定标准（迭代 5 验收要求）：
  - 成功条数 == 1
  - 其余全部返回 409（SLOT_OCCUPIED）
  - 数据库里该实验室+日期+时段只有 1 条占用中的预约
"""

import argparse
import datetime
import json
import threading
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
TEACHER = "tea1001"
LAB_ADMIN = "labadmin"
LAB_ID = 5


def call(path, body=None, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body or {}).encode(), method="POST")
    req.add_header("Content-Type", "application/json;charset=UTF-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except Exception:
            return e.code, {"raw": payload[:200].decode("utf-8", "ignore")}


def login(username):
    status, body = call("/auth/login", {"username": username, "password": "123456"})
    token = (body.get("data") or {}).get("accessToken")
    if not token:
        raise SystemExit(f"登录失败：{username} -> {status} {body}")
    return token


def next_weekday(days):
    d = datetime.date.today() + datetime.timedelta(days=days)
    while d.isoweekday() > 5:
        d += datetime.timedelta(days=1)
    return d


def main():
    parser = argparse.ArgumentParser(description="预约并发压测")
    parser.add_argument("-n", "--concurrency", type=int, default=50)
    parser.add_argument("--keep", action="store_true", help="保留压测产生的预约")
    args = parser.parse_args()

    teacher = login(TEACHER)
    lab_admin = login(LAB_ADMIN)

    _, body = call("/laboratory/get_info", {"id": LAB_ID}, token=teacher)
    lab = body.get("data") or {}
    _, body = call("/class/get_list", {}, token=teacher)
    classes = body.get("data") or []
    if not lab or not classes:
        raise SystemExit("缺少实验室或班级数据，请先 ./dev.sh start 重置种子数据")

    # 找一个未来工作日里空闲的时段
    day = slot_id = None
    for offset in range(3, 20):
        candidate_day = next_weekday(offset).isoformat()
        _, body = call("/laboratory/get_availability", {"id": LAB_ID, "date": candidate_day}, token=teacher)
        for slot in (body.get("data") or {}).get("slots") or []:
            if slot["available"]:
                day, slot_id = candidate_day, slot["slotId"]
                break
        if day:
            break
    if not day:
        raise SystemExit("未来两周内该实验室没有空闲时段，请换一个 LAB_ID")

    payload = {
        "resourceType": 1, "labId": LAB_ID, "reserveDate": day, "slotId": slot_id,
        "usePeople": 20, "experimentTypeId": lab.get("experimentTypeId"),
        "classIds": [classes[0]["id"]], "courseName": "并发压测课程",
        "purpose": "并发用例：同一时段 N 个线程同时提交"
    }

    print(f"目标：实验室 #{LAB_ID}（{lab.get('labNo')}） {day} 时段 #{slot_id}")
    print(f"并发：{args.concurrency} 个线程同时提交\n")

    results = []
    lock = threading.Lock()
    barrier = threading.Barrier(args.concurrency)

    def worker():
        try:
            barrier.wait(timeout=30)  # 让所有线程尽量同时到达
        except threading.BrokenBarrierError:
            pass
        status, body = call("/reservation/create", payload, token=teacher)
        with lock:
            results.append((status, (body.get("data") or {}).get("conflictCode") if status != 200 else None))

    started = time.time()
    threads = [threading.Thread(target=worker) for _ in range(args.concurrency)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - started

    ok = [item for item in results if item[0] == 200]
    conflicts = [item for item in results if item[0] == 409]
    others = [item for item in results if item[0] not in (200, 409)]

    print(f"耗时 {elapsed:.2f}s")
    print(f"成功 {len(ok)} 条 / 409 {len(conflicts)} 条 / 其他 {len(others)} 条")
    if others:
        print("其他状态分布：", {code: sum(1 for item in others if item[0] == code) for code, _ in others})

    _, body = call("/reservation/get_page",
                   {"pageNum": 1, "pageSize": 50, "labId": LAB_ID, "reserveDateBegin": day,
                    "reserveDateEnd": day, "status": "PENDING"}, token=lab_admin)
    rows = [item for item in (body.get("data") or {}).get("records") or [] if item["slotId"] == slot_id]
    print(f"数据库中该时段占用中的预约：{len(rows)} 条")

    passed = len(ok) == 1 and len(conflicts) == args.concurrency - 1 and len(rows) == 1
    print("\n结论：" + ("PASS —— 恰好 1 条成功，其余全部 409" if passed else "FAIL —— 并发控制不达标"))

    if not args.keep:
        for row in rows:
            call("/reservation/release", {"id": row["id"], "reason": "并发压测清理"}, token=lab_admin)
        print("已清理压测产生的预约")

    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
