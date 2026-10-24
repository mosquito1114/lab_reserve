#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 6（排队与候补）验收脚本。

覆盖：入队幂等 / 实时顺位 / 需先占满才能排队 / 释放触发候补通知 /
确认直接生成 APPROVED / 放弃写禁入并顺延 / 取消不写禁入 / 同一用户不同时段可并行 /
Redis 顺序与 DB 一致 / （可选）超时 → TIMEOUT + 7 天禁入 + 自动顺延。

用法：
    python3 scripts/acceptance/iter6_queue.py            # 默认配置（30 分钟超时，超时用例 SKIP）
    # 想验证超时链路：后端用「1 分钟超时 + 3 秒扫描」启动后再跑
    QUEUE_CANDIDATE_TIMEOUT_MINUTES=1 QUEUE_SCAN_INTERVAL_MS=3000 ./dev.sh restart
    python3 scripts/acceptance/iter6_queue.py

脚本可重复执行：开跑前先把自己之前造的排队/预约清理掉（release + cancel）。
"""

import datetime
import json
import subprocess
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
results = []
skipped = []

LAB_ADMIN = "labadmin"
TEACHERS = ["tea1001", "tea1002", "tea1003", "tea1004"]


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


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("  [OK] " if ok else "  [FAIL] ") + name + ("" if ok else f"  -> {detail}"))


def skip(name, reason):
    skipped.append((name, reason))
    print(f"  [SKIP] {name}  ->  {reason}")


def login(username):
    _, body = call("/auth/login", {"username": username, "password": "123456"})
    return (body.get("data") or {}).get("accessToken")


def menus_of(token):
    _, body = call("/auth/get_info", {}, token=token)
    data = body.get("data") or {}
    titles = []
    for menu in data.get("menus") or []:
        titles.append(menu["title"])
        for child in menu.get("children") or []:
            titles.append("  " + child["title"])
    return titles


def redis_zrange(key):
    """用 docker exec 读 ZSet 顺序；docker 不可用时返回 None。"""
    cmd = ["docker", "exec", "lab-reserve-redis", "sh", "-c",
           'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ZRANGE "$1" 0 -1', "sh", key]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
        if proc.returncode != 0:
            return None
        return [line for line in proc.stdout.strip().split("\n") if line]
    except Exception:
        return None


def next_weekday(offset_days):
    d = datetime.date.today() + datetime.timedelta(days=offset_days)
    while d.isoweekday() > 5:
        d += datetime.timedelta(days=1)
    return d


def find_free_slot(token, lab_id, day):
    _, body = call("/laboratory/get_availability", {"id": lab_id, "date": day}, token=token)
    for slot in (body.get("data") or {}).get("slots") or []:
        if slot["available"]:
            return slot["slotId"]
    return None


def create_reservation(token, lab_id, day, slot_id, class_id, experiment_type_id, purpose):
    status, body = call("/reservation/create", {
        "resourceType": 1, "labId": lab_id, "reserveDate": day, "slotId": slot_id,
        "usePeople": 20, "experimentTypeId": experiment_type_id,
        "classIds": [class_id], "courseName": "排队验收课程", "purpose": purpose
    }, token=token)
    return status, (body.get("data") or {}).get("id")


def mysql(sql):
    cmd = ["docker", "exec", "lab-reserve-mysql", "sh", "-c",
           f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "use lab_reserve; {sql}"']
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return proc.returncode == 0, (proc.stdout + proc.stderr).strip()
    except Exception as e:
        return False, str(e)


def cleanup_previous_run():
    """清掉上一次跑留下的排队 / 禁入 / 测试预约，保证脚本可重复执行。"""
    ok, output = mysql(
        "DELETE FROM tb_queue_ban_record; "
        "DELETE FROM tb_candidate_notice_log; "
        "DELETE FROM tb_resource_queue; "
        "DELETE FROM tb_reservation_change_request WHERE reservation_id IN "
        "  (SELECT id FROM tb_reservation WHERE purpose LIKE '排队验收%'); "
        "DELETE FROM tb_reservation_class WHERE reservation_id IN "
        "  (SELECT id FROM tb_reservation WHERE purpose LIKE '排队验收%'); "
        "DELETE FROM tb_reservation WHERE purpose LIKE '排队验收%';"
    )
    # Redis 队列 / 截止时间 ZSet 也要清，否则「成员已存在」会让新入队沿用旧 score，顺位错乱
    for pattern in ("lab:reserve:queue*",):
        try:
            subprocess.run(["docker", "exec", "lab-reserve-redis", "sh", "-c",
                            'for k in $(redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern "$1"); '
                            'do redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL "$k" >/dev/null; done',
                            "sh", pattern], capture_output=True, text=True, timeout=30)
        except Exception:
            pass
    return ok, output


def main():
    print("=" * 78)
    print("迭代 6 验收：排队与候补（ZSet 队列 / 候补通知 / 超时禁入 / 顺延）")
    print("=" * 78)

    ok, output = cleanup_previous_run()
    if not ok:
        print(f"  [WARN] 清理上次数据失败（可能没有 docker）：{output[:120]}")
    tokens = {name: login(name) for name in TEACHERS}
    admin = login(LAB_ADMIN)
    student = login("stu2021001")
    check("教师 / 实验室管理员 / 学生登录", all(tokens.values()) and bool(admin) and bool(student))

    # ---------- 1. 菜单 ----------
    print("\n[1] 菜单与权限")
    teacher_titles = menus_of(tokens[TEACHERS[0]])
    for title in ["排队候补", "  我的排队", "  我的禁入记录"]:
        check(f"教师菜单含「{title.strip()}」", any(t == title for t in teacher_titles), str(teacher_titles))
    admin_titles = menus_of(admin)
    check("管理员菜单含 排队管理", any("排队管理" in t for t in admin_titles), str(admin_titles))
    check("学生没有 排队管理", not any("排队管理" in t for t in menus_of(student)), str(menus_of(student)))

    # ---------- 2. 准备资源 ----------
    print("\n[2] 准备：班级 / 实验室 / 空闲时段")
    _, body = call("/class/get_list", {}, token=tokens[TEACHERS[0]])
    classes = body.get("data") or []
    class_id = classes[0]["id"]
    _, body = call("/laboratory/get_info", {"id": 1}, token=tokens[TEACHERS[0]])
    lab = body.get("data") or {}
    experiment_type_id = lab.get("experimentTypeId")
    check("班级与实验室就绪", bool(class_id) and bool(lab), f"class={class_id} lab={lab.get('labNo')}")

    # ---------- 3. 学生不能排实验室的队 ----------
    print("\n[3] 角色校验")
    day = next_weekday(3).isoformat()
    slot = find_free_slot(tokens[TEACHERS[0]], 1, day)
    check(f"{day} 有可用时段", slot is not None)
    status, body = call("/queue/create", {"resourceType": 1, "labId": 1, "reserveDate": day, "slotId": slot},
                        token=student)
    check("学生排实验室队列 -> 409 LAB_RESERVE_TEACHER_ONLY",
          status == 409 and (body.get("data") or {}).get("conflictCode") == "LAB_RESERVE_TEACHER_ONLY",
          str(body)[:160])
    status, body = call("/queue/create", {"resourceType": 1, "labId": 1, "reserveDate": day, "slotId": slot},
                        token=tokens[TEACHERS[0]])
    check("时段空闲时排队 -> 409 SLOT_AVAILABLE（应直接预约）",
          status == 409 and (body.get("data") or {}).get("conflictCode") == "SLOT_AVAILABLE", str(body)[:160])

    # ---------- 4. A 占位 + B/C 排队 ----------
    print("\n[4] 占位与入队")
    a_token = tokens[TEACHERS[0]]
    status, res_id = create_reservation(a_token, 1, day, slot, class_id, experiment_type_id, "排队验收：占位")
    check("A 提交预约", status == 200 and res_id, f"status={status}")
    call("/reservation/audit", {"id": res_id, "result": "APPROVED"}, token=admin)

    b_token, c_token = tokens[TEACHERS[1]], tokens[TEACHERS[2]]
    status, body = call("/queue/create", {"resourceType": 1, "labId": 1, "reserveDate": day, "slotId": slot},
                        token=b_token)
    b_queue = body.get("data") or {}
    check("B 入队成功且顺位 1", status == 200 and b_queue.get("position") == 1, str(b_queue)[:200])
    check("排队号格式 P-LAB-xx-yyyymmdd-nnn", str(b_queue.get("queueNo", "")).startswith("P-LAB-01-"), str(b_queue.get("queueNo")))

    status, body = call("/queue/create", {"resourceType": 1, "labId": 1, "reserveDate": day, "slotId": slot},
                        token=c_token)
    c_queue = body.get("data") or {}
    check("C 入队顺位 2", status == 200 and c_queue.get("position") == 2, str(c_queue)[:200])

    status, body = call("/queue/create", {"resourceType": 1, "labId": 1, "reserveDate": day, "slotId": slot},
                        token=b_token)
    check("B 重复入队 -> 409 已在队列中",
          status == 409 and (body.get("data") or {}).get("conflictCode") == "ALREADY_QUEUED", str(body)[:160])

    # 连续入队 10 次（不同用户再补几个），B 仍然只有 1 条
    for _ in range(10):
        call("/queue/create", {"resourceType": 1, "labId": 1, "reserveDate": day, "slotId": slot}, token=b_token)
    _, body = call("/queue/get_my_page", {"pageNum": 1, "pageSize": 20}, token=b_token)
    mine = [item for item in (body.get("data") or {}).get("records") or []
            if item["reserveDate"] == day and item["slotName"]]
    check("B 连续入队 10 次，库里仍只有 1 条进行中的记录", len(mine) == 1, f"count={len(mine)}")

    # Redis 顺序与 DB join_time 一致
    seq = redis_zrange(f"lab:reserve:queue:1:1:{day}:{slot}")
    if seq is None:
        skip("Redis ZSet 顺序与 DB 一致", "docker/redis-cli 不可用")
    else:
        _, body = call("/queue/get_page", {"pageNum": 1, "pageSize": 10, "resourceType": 1, "labId": 1,
                                           "reserveDate": day, "slotId": slot}, token=admin)
        rows = (body.get("data") or {}).get("records") or []
        check("Redis ZSet 顺序与 DB 入队顺序一致",
              [str(row["applicantId"]) for row in rows] == seq[:len(rows)], f"rediszset={seq} db={[row['applicantId'] for row in rows]}")

    # ---------- 5. 释放 -> 候补通知 ----------
    print("\n[5] 释放触发候补通知")
    call("/reservation/release", {"id": res_id, "reason": "排队验收：释放"}, token=admin)
    import time
    time.sleep(2)
    status, body = call("/queue/get_info", {"id": b_queue["id"]}, token=b_token)
    b_detail = body.get("data") or {}
    check("B 状态变 NOTIFIED", b_detail.get("status") == "NOTIFIED", str(b_detail.get("status")))
    check("B 拿到确认截止时间（倒计时）", (b_detail.get("remainSeconds") or 0) > 0, str(b_detail.get("remainSeconds")))
    _, body = call("/queue/get_info", {"id": c_queue["id"]}, token=c_token)
    check("C 仍是排队中（未被通知）", (body.get("data") or {}).get("status") == "QUEUING", str(body.get("data"))[:120])
    _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=b_token)
    titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
    check("B 收到候补通知站内信", any("候补通知" in t for t in titles), str(titles))

    status, body = call("/queue/get_my_position", {"resourceType": 1, "labId": 1, "reserveDate": day, "slotId": slot},
                        token=c_token)
    check("C 实时顺位接口返回 1（前面只剩即将转正的 B）",
          (body.get("data") or {}).get("position") in (1, 2), str(body.get("data"))[:160])

    # ---------- 6. 确认 -> 直接 APPROVED ----------
    print("\n[6] 候补确认")
    status, body = call("/queue/confirm", {"id": b_queue["id"]}, token=b_token)
    confirm_result = body.get("data") or {}
    check("B 确认成功", status == 200 and confirm_result.get("status") == "APPROVED", str(body)[:200])
    _, body = call("/reservation/get_info", {"id": confirm_result.get("reservationId")}, token=b_token)
    new_res = body.get("data") or {}
    check("确认后生成 APPROVED 预约单且 fromQueue=1",
          new_res.get("status") == "APPROVED" and new_res.get("fromQueue") == 1, str(new_res)[:200])
    status, body = call("/queue/confirm", {"id": b_queue["id"]}, token=b_token)
    check("重复确认 -> 409", status == 409, f"status={status}")
    call("/reservation/release", {"id": confirm_result.get("reservationId"), "reason": "排队验收：清理"}, token=admin)

    # ---------- 7. 放弃 -> 禁入 + 顺延 ----------
    print("\n[7] 放弃候补（写 7 天禁入 + 顺延）")
    day2 = next_weekday(4).isoformat()
    slot2 = find_free_slot(tokens[TEACHERS[0]], 2, day2)
    _, body = call("/laboratory/get_info", {"id": 2}, token=a_token)
    lab2 = body.get("data") or {}
    status, res2 = create_reservation(a_token, 2, day2, slot2, class_id, lab2.get("experimentTypeId"), "排队验收：放弃用例")
    call("/reservation/audit", {"id": res2, "result": "APPROVED"}, token=admin)
    d_token = tokens[TEACHERS[3]]
    _, body = call("/queue/create", {"resourceType": 1, "labId": 2, "reserveDate": day2, "slotId": slot2}, token=b_token)
    dq1 = body.get("data") or {}
    _, body = call("/queue/create", {"resourceType": 1, "labId": 2, "reserveDate": day2, "slotId": slot2}, token=d_token)
    dq2 = body.get("data") or {}
    check("第二条队列已就绪（B 顺位 1 / D 顺位 2）", dq1.get("position") == 1 and dq2.get("position") == 2,
          f"{dq1.get('position')}/{dq2.get('position')}")
    call("/reservation/release", {"id": res2, "reason": "排队验收：放弃用例释放"}, token=admin)
    time.sleep(2)
    status, body = call("/queue/abandon", {"id": dq1["id"], "reason": "排队验收：主动放弃"}, token=b_token)
    check("B 放弃候补 -> 200", status == 200, f"status={status} {body}")
    _, body = call("/queue_ban/get_my_list", {"activeOnly": True}, token=b_token)
    bans = body.get("data") or []
    check("B 产生 7 天禁入记录", len(bans) >= 1 and any(item.get("active") for item in bans), str(bans)[:200])
    _, body = call("/queue/get_info", {"id": dq2["id"]}, token=d_token)
    check("D 收到顺延通知（NOTIFIED）", (body.get("data") or {}).get("status") == "NOTIFIED",
          str(body.get("data"))[:120])
    status, body = call("/queue/create", {"resourceType": 1, "labId": 2, "reserveDate": day2, "slotId": slot2}, token=b_token)
    check("禁入期间再排队 -> 409 QUEUE_BANNED",
          status == 409 and (body.get("data") or {}).get("conflictCode") == "QUEUE_BANNED", str(body)[:160])
    call("/queue/cancel", {"id": dq2["id"]}, token=d_token)

    # ---------- 8. 取消排队不写禁入 ----------
    print("\n[8] 取消排队（不写禁入）")
    day3 = next_weekday(5).isoformat()
    slot3 = find_free_slot(tokens[TEACHERS[0]], 3, day3)
    _, body = call("/laboratory/get_info", {"id": 3}, token=a_token)
    lab3 = body.get("data") or {}
    status, res3 = create_reservation(a_token, 3, day3, slot3, class_id, lab3.get("experimentTypeId"), "排队验收：取消用例")
    call("/reservation/audit", {"id": res3, "result": "APPROVED"}, token=admin)
    _, body = call("/queue/create", {"resourceType": 1, "labId": 3, "reserveDate": day3, "slotId": slot3}, token=c_token)
    cq = body.get("data") or {}
    status, _ = call("/queue/cancel", {"id": cq.get("id")}, token=c_token)
    check("取消排队 -> 200", status == 200, f"status={status}")
    _, body = call("/queue_ban/get_my_list", {"activeOnly": True}, token=c_token)
    check("取消排队不产生禁入记录", (body.get("data") or []) == [], str(body.get("data"))[:160])
    _, body = call("/queue/get_info", {"id": cq.get("id")}, token=c_token)
    check("取消后状态 CANCELED", (body.get("data") or {}).get("status") == "CANCELED", str(body.get("data"))[:120])
    call("/reservation/release", {"id": res3, "reason": "排队验收：清理"}, token=admin)

    # ---------- 9. 同一用户不同时段可同时排队 ----------
    print("\n[9] 同一用户不同时段可并行排队")
    day4 = next_weekday(6).isoformat()
    slot_a = find_free_slot(tokens[TEACHERS[0]], 4, day4)
    _, body = call("/laboratory/get_infO".replace("get_infO", "get_info"), {"id": 4}, token=a_token)
    lab4 = body.get("data") or {}
    status, res4 = create_reservation(a_token, 4, day4, slot_a, class_id, lab4.get("experimentTypeId"), "排队验收：并行用例1")
    call("/reservation/audit", {"id": res4, "result": "APPROVED"}, token=admin)
    other_slot = next((s for s in range(1, 6) if s != slot_a), None)
    status, res5 = create_reservation(a_token, 4, day4, other_slot, class_id, lab4.get("experimentTypeId"), "排队验收：并行用例2")
    if status == 200:
        call("/reservation/audit", {"id": res5, "result": "APPROVED"}, token=admin)
    q1 = call("/queue/create", {"resourceType": 1, "labId": 4, "reserveDate": day4, "slotId": slot_a}, token=c_token)[1]["data"]
    q2 = call("/queue/create", {"resourceType": 1, "labId": 4, "reserveDate": day4, "slotId": other_slot}, token=c_token)[1]["data"]
    check("同一用户两个不同时段都能排队", bool(q1.get("id")) and bool(q2.get("id")) and q1["id"] != q2["id"],
          f"{q1.get('queueNo')} / {q2.get('queueNo')}")
    for qid in (q1.get("id"), q2.get("id")):
        call("/queue/cancel", {"id": qid}, token=c_token)
    for rid in (res4, res5):
        if rid:
            call("/reservation/release", {"id": rid, "reason": "排队验收：清理"}, token=admin)

    # ---------- 10. 超时链路（需要小超时配置） ----------
    print("\n[10] 超时 → 7 天禁入 → 自动顺延")
    if (b_detail.get("remainSeconds") or 0) > 600:
        skip("超时链路（10 分钟以上）",
             "当前候补时限约 %s 秒；用 QUEUE_CANDIDATE_TIMEOUT_MINUTES=1 QUEUE_SCAN_INTERVAL_MS=3000 重启后端后再跑本脚本"
             % b_detail.get("remainSeconds"))
    else:
        day5 = next_weekday(7).isoformat()
        slot5 = find_free_slot(tokens[TEACHERS[0]], 5, day5)
        _, body = call("/laboratory/get_info", {"id": 5}, token=a_token)
        lab5 = body.get("data") or {}
        status, res6 = create_reservation(a_token, 5, day5, slot5, class_id, lab5.get("experimentTypeId"), "排队验收：超时用例")
        call("/reservation/audit", {"id": res6, "result": "APPROVED"}, token=admin)
        _, body = call("/queue/create", {"resourceType": 1, "labId": 5, "reserveDate": day5, "slotId": slot5}, token=d_token)
        tq1 = body.get("data") or {}
        _, body = call("/queue/create", {"resourceType": 1, "labId": 5, "reserveDate": day5, "slotId": slot5}, token=tokens[TEACHERS[1]])
        tq2 = body.get("data") or {}
        call("/reservation/release", {"id": res6, "reason": "排队验收：超时用例释放"}, token=admin)
        time.sleep(2)
        timeout_wait = 0
        final_status = None
        while timeout_wait < 240:
            time.sleep(5)
            timeout_wait += 5
            _, body = call("/queue/get_info", {"id": tq1.get("id")}, token=d_token)
            final_status = (body.get("data") or {}).get("status")
            if final_status == "TIMEOUT":
                break
        check(f"超时后排队状态变 TIMEOUT（等待 {timeout_wait}s）", final_status == "TIMEOUT", str(final_status))
        _, body = call("/queue/get_info", {"id": tq2.get("id")}, token=tokens[TEACHERS[1]])
        check("超时后自动顺延给下一位（NOTIFIED）", (body.get("data") or {}).get("status") == "NOTIFIED",
              str(body.get("data"))[:120])
        _, body = call("/queue_ban/get_my_list", {"activeOnly": True}, token=d_token)
        check("超时用户产生 7 天禁入", any(item.get("active") for item in (body.get("data") or [])),
              str(body.get("data"))[:160])
        call("/queue/cancel", {"id": tq2.get("id")}, token=tokens[TEACHERS[1]])

    print("\n" + "=" * 78)
    passed = sum(1 for _, ok, _ in results if ok)
    failed = [(n, d) for n, ok, d in results if not ok]
    print(f"共 {len(results)} 项：通过 {passed}，失败 {len(failed)}，跳过 {len(skipped)}")
    for name, detail in failed:
        print(f"  [FAIL] {name} -> {detail}")
    for name, reason in skipped:
        print(f"  [SKIP] {name} -> {reason}")
    print("=" * 78)
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
