#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 5（预约管理）验收脚本。

覆盖：角色与班级校验、实验类型匹配、冲突与库存边界、审核与通知、
调整申请（24h 窗口 / 通过前不改字段）、释放、我的实验课表。
50 并发压测在 scripts/stress/reservation_concurrency.py（本脚本也内联跑一次 20 并发）。

用法：
  1) ./dev.sh start
  2) python3 scripts/acceptance/iter5_reservation.py

说明：
  - 脚本可重复执行：每次都新建预约，用当天日期+3 天的空闲时段，跑完把测试预约释放掉。
  - 依赖种子数据里的教师/班级/实验室/设备。
"""

import datetime
import json
import subprocess
import threading
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
results = []
skipped = []

TEACHER = "tea1001"
STUDENT = "stu2021001"
LAB_ADMIN = "labadmin"


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
    status, body = call("/auth/login", {"username": username, "password": "123456"})
    return (body.get("data") or {}).get("accessToken")


def menus_of(token):
    _, body = call("/auth/get_info", {}, token=token)
    data = body.get("data") or {}
    titles = []
    for menu in data.get("menus") or []:
        titles.append(menu["title"])
        for child in menu.get("children") or []:
            titles.append("  " + child["title"])
    return titles, (data.get("permissions") or [])


def run_sql(sql):
    """执行一条 SQL（造/清测试夹具、验过期扫描）；没有 docker 时返回 (False, 原因)。"""
    cmd = ["docker", "exec", "lab-reserve-mysql", "sh", "-c",
           f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "use lab_reserve; {sql}"']
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return proc.returncode == 0, (proc.stdout + proc.stderr).strip()
    except Exception as e:
        return False, str(e)


_MYSQL_OK = None


def mysql_available():
    global _MYSQL_OK
    if _MYSQL_OK is None:
        ok, _ = run_sql("SELECT 1;")
        _MYSQL_OK = ok
    return _MYSQL_OK


def next_weekday(offset_days):
    """今天 + offset 天，如果落在周末就顺延到下周一（实验室只在工作日开放）"""
    d = datetime.date.today() + datetime.timedelta(days=offset_days)
    while d.isoweekday() > 5:
        d += datetime.timedelta(days=1)
    return d


def find_free_lab_slot(token, lab_id, day):
    _, body = call("/laboratory/get_availability", {"id": lab_id, "date": day}, token=token)
    for slot in (body.get("data") or {}).get("slots") or []:
        if slot["available"]:
            return slot["slotId"]
    return None


def find_free_equip_slot(token, equip_id, day):
    _, body = call("/equipment/get_availability", {"id": equip_id, "date": day}, token=token)
    for slot in (body.get("data") or {}).get("slots") or []:
        if slot["available"]:
            return slot["slotId"]
    return None


def create_reservation(token, lab_id, day, slot_id, class_ids, experiment_type_id, purpose, equipments=None):
    payload = {
        "resourceType": 1, "labId": lab_id, "reserveDate": day, "slotId": slot_id,
        "usePeople": 20, "experimentTypeId": experiment_type_id,
        "classIds": class_ids, "courseName": "迭代5验收课程", "purpose": purpose
    }
    if equipments:
        payload["equipments"] = equipments
    status, body = call("/reservation/create", payload, token=token)
    return status, (body.get("data") or {}).get("id")


def lab_submit_payload(lab_id, day, slot_id, class_ids, experiment_type_id):
    return {
        "resourceType": 1, "labId": lab_id, "reserveDate": day, "slotId": slot_id,
        "usePeople": 30, "experimentTypeId": experiment_type_id,
        "classIds": class_ids, "courseName": "迭代5验收课程", "purpose": "迭代 5 验收：预约提交与冲突判定"
    }


TEST_PURPOSES = ("迭代 5 验收", "24h 窗口用例", "并发用例")


def cleanup_test_reservations(student, lab_admin):
    """把上一次运行留下的测试预约清掉（含 CHANGE_PENDING：先取消调整申请再释放）"""
    _, body = call("/reservation/get_my_page", {"pageNum": 1, "pageSize": 100}, token=student)
    for row in (body.get("data") or {}).get("records") or []:
        if not any(row.get("purpose", "").startswith(prefix) for prefix in TEST_PURPOSES):
            continue
        if row["status"] == "CHANGE_PENDING":
            _, detail = call("/reservation/get_info", {"id": row["id"]}, token=student)
            change = (detail.get("data") or {}).get("changePending") or {}
            if change.get("id"):
                call("/reservation_change/cancel", {"id": change["id"]}, token=student)
        call("/reservation/release", {"id": row["id"], "reason": "上一次验收残留清理"}, token=lab_admin)


def main():
    print("=" * 78)
    print("迭代 5 验收：预约管理（预检 / 提交 / 审核 / 调整 / 课表 / 并发）")
    print("=" * 78)

    teacher = login(TEACHER)
    student = login(STUDENT)
    lab_admin = login(LAB_ADMIN)
    check("教师 / 学生 / 实验室管理员登录", all([teacher, student, lab_admin]))

    # 清理上一次跑残留的测试预约（保持可重复执行）
    cleanup_test_reservations(student, lab_admin)

    # ---------- 1. 菜单与权限 ----------
    print("\n[1] 菜单与权限")
    teacher_titles, teacher_perms = menus_of(teacher)
    for title in ["预约管理", "  预约申请", "  我的预约"]:
        check(f"教师菜单含「{title.strip()}」", any(t == title for t in teacher_titles), str(teacher_titles))
    check("教师菜单不含 预约审核（审核是实验室管理员的活）",
          not any("预约审核" in t for t in teacher_titles), str(teacher_titles))
    student_titles, _ = menus_of(student)
    check("学生菜单不含 预约审核", not any("预约审核" in t for t in student_titles), str(student_titles))
    check("学生菜单含 我的实验课表", any("我的实验课表" in t for t in student_titles), str(student_titles))
    admin_titles, _ = menus_of(lab_admin)
    for title in ["  预约审核", "  调整审批"]:
        check(f"实验室管理员菜单含「{title.strip()}」", any(t == title for t in admin_titles), str(admin_titles))
    check("学生调管理员分页 get_page -> 403",
          call("/reservation/get_page", {"pageNum": 1, "pageSize": 5}, token=student)[0] == 403)

    # ---------- 2. 资源与班级准备 ----------
    print("\n[2] 准备：取实验室 / 设备 / 班级 / 实验类型")
    _, body = call("/class/get_list", {}, token=teacher)
    classes = body.get("data") or []
    check("班级列表可用", len(classes) > 0, str(classes)[:120])
    class_ids = [item["id"] for item in classes[:2]]

    _, body = call("/laboratory/get_info", {"id": 1}, token=teacher)
    lab = body.get("data") or {}
    _, body = call("/experiment_type/get_list", {"status": 1}, token=teacher)
    types = body.get("data") or []
    wrong_type = next((item["id"] for item in types if item["id"] != lab.get("experimentTypeId")), None)
    check("实验室与实验类型就绪", bool(lab) and wrong_type is not None, str(lab)[:120])

    day_plus3 = next_weekday(3).isoformat()
    slot_id = find_free_lab_slot(teacher, 1, day_plus3)
    if slot_id is None:
        slot_id = find_free_lab_slot(teacher, 2, day_plus3)
        lab_id = 2
    else:
        lab_id = 1
    check(f"{day_plus3} 有可预约的实验室时段", slot_id is not None, "没有空闲时段，请检查种子数据")

    submit = lab_submit_payload(lab_id, day_plus3, slot_id, class_ids, lab.get("experimentTypeId"))

    # ---------- 3. 角色与班级校验 ----------
    print("\n[3] 角色 / 班级 / 实验类型校验")
    status, body = call("/reservation/check", submit, token=student)
    check("学生预检实验室 -> LAB_RESERVE_TEACHER_ONLY",
          (body.get("data") or {}).get("conflict", {}).get("code") == "LAB_RESERVE_TEACHER_ONLY",
          str(body.get("data"))[:200])
    check("学生提交实验室 -> 409",
          call("/reservation/create", submit, token=student)[0] == 409)

    status, body = call("/reservation/create", dict(submit, classIds=[]), token=teacher)
    check("教师不选班级 -> 409 CLASS_REQUIRED",
          status == 409 and (body.get("data") or {}).get("conflictCode") == "CLASS_REQUIRED", str(body)[:200])

    status, body = call("/reservation/create", dict(submit, experimentTypeId=wrong_type), token=teacher)
    check("实验类型与实验室不匹配 -> 409",
          status == 409 and (body.get("data") or {}).get("conflictCode") == "EXPERIMENT_TYPE_NOT_SUPPORTED",
          str(body)[:200])

    # ---------- 4. 教师提交 + 学生设备预约 ----------
    print("\n[4] 提交预约")
    status, body = call("/reservation/check", submit, token=teacher)
    check("教师预检可预约", (body.get("data") or {}).get("available") is True, str(body.get("data"))[:200])

    status, body = call("/reservation/create", submit, token=teacher)
    created = body.get("data") or {}
    res_id = created.get("id")
    check("教师提交实验室预约 -> PENDING", status == 200 and created.get("status") == "PENDING", str(body)[:200])
    check("返回预约号 R-YYYYMMDD-XXXX",
          str(created.get("reserveNo", "")).startswith("R-") and len(created.get("reserveNo", "")) == 15,
          str(created.get("reserveNo")))

    status, body = call("/reservation/create", submit, token=teacher)
    check("同实验室同时段重复提交 -> 409",
          status == 409 and (body.get("data") or {}).get("conflictCode") == "SLOT_OCCUPIED", str(body)[:160])

    _, body = call("/equipment/list_option", {"labId": lab_id}, token=student)
    equip = (body.get("data") or [{}])[0]
    equip_day = (datetime.date.today() + datetime.timedelta(days=2)).isoformat()
    equip_slot = find_free_equip_slot(student, equip.get("id"), equip_day) or 1
    equip_submit = {
        "resourceType": 2, "equipmentId": equip.get("id"), "reserveDate": equip_day, "slotId": equip_slot,
        "usePeople": 1, "experimentTypeId": types[0]["id"], "purpose": "迭代 5 验收：设备借用"
    }
    status, body = call("/reservation/create", equip_submit, token=student)
    student_res = body.get("data") or {}
    check("学生提交设备预约 -> 成功", status == 200 and student_res.get("status") == "PENDING", str(body)[:200])

    # ---------- 5. 待审核 / 课表 ----------
    print("\n[5] 待审核列表与我的实验课表")
    status, body = call("/reservation/get_pending_page", {"pageNum": 1, "pageSize": 50}, token=lab_admin)
    pending_ids = [item["id"] for item in (body.get("data") or {}).get("records") or []]
    check("待审核列表能看到刚提交的预约", status == 200 and res_id in pending_ids, str(pending_ids[:10]))

    status, body = call("/reservation/get_my_timetable", {}, token=student)
    timetable = body.get("data") or []
    check("学生课表有数据（种子 + 本班已通过预约）", status == 200 and len(timetable) > 0, f"len={len(timetable)}")
    if timetable:
        first = timetable[0]
        check("课表字段完整（实验室/位置/节次/教师）",
              all(first.get(k) for k in ["labNo", "labLocation", "slotName", "teacherName"]),
              json.dumps(first, ensure_ascii=False)[:200])
    check("教师没有 reservation:timetable，调课表接口 -> 403",
          call("/reservation/get_my_timetable", {}, token=teacher)[0] == 403)

    # ---------- 6. 审核 ----------
    print("\n[6] 审核与通知")
    status, _ = call("/reservation/audit", {"id": res_id, "result": "REJECTED"}, token=lab_admin)
    check("驳回不填原因 -> 400", status == 400, f"status={status}")
    status, _ = call("/reservation/audit", {"id": res_id, "result": "APPROVED"}, token=lab_admin)
    check("审核通过 -> 200", status == 200, f"status={status}")
    status, _ = call("/reservation/audit", {"id": res_id, "result": "APPROVED"}, token=lab_admin)
    check("重复审核 -> 409", status == 409, f"status={status}")
    _, body = call("/reservation/get_info", {"id": res_id}, token=teacher)
    check("审核后状态 APPROVED 且有审核人", (body.get("data") or {}).get("status") == "APPROVED"
          and (body.get("data") or {}).get("auditUserName"), str(body.get("data"))[:160])

    _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=teacher)
    titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
    check("教师收到审核结果站内信", any("审核" in t for t in titles), str(titles))

    # ---------- 7. 调整申请 ----------
    print("\n[7] 调整申请（24h 窗口 / 通过前不改字段）")
    # 设备预约可以做「不足 24 小时」的用例（设备不受开放日限制）
    soon_day = (datetime.date.today() + datetime.timedelta(days=1)).isoformat()
    soon_id = None
    soon_token = None
    body = {}
    now = datetime.datetime.now()
    # 设备不受开放日限制，可以造出"明天就上机"的用例；
    # 种子数据里每位同学在 10-25 可能已有预约，所以轮换几个账号直到能造出用例
    for account in ["stu2021001", "stu2021002", "stu2021003", "stu2021004", "stu2021005"]:
        token = student if account == "stu2021001" else login(account)
        _, avail = call("/equipment/get_availability", {"id": equip.get("id"), "date": soon_day}, token=token)
        for candidate in (avail.get("data") or {}).get("slots") or []:
            if not candidate["available"]:
                continue
            start_at = datetime.datetime.combine(
                datetime.date.fromisoformat(soon_day),
                datetime.time.fromisoformat(candidate["startTime"]))
            if (start_at - now).total_seconds() >= 24 * 3600:
                continue
            status, body = call("/reservation/create", {
                "resourceType": 2, "equipmentId": equip.get("id"), "reserveDate": soon_day,
                "slotId": candidate["slotId"], "usePeople": 1,
                "experimentTypeId": types[0]["id"], "purpose": "24h 窗口用例"
            }, token=token)
            if status == 200:
                soon_id = (body.get("data") or {}).get("id")
                soon_token = token
                break
        if soon_id:
            break
    if soon_id:
        call("/reservation/audit", {"id": soon_id, "result": "APPROVED"}, token=lab_admin)
        status, body = call("/reservation_change/create",
                            {"reservationId": soon_id, "changeType": 2, "reason": "临时取消"}, token=soon_token)
        check("距开始不足 24h 发起调整 -> 409", status == 409 and "24" in (body.get("message") or ""),
              f"status={status} {body.get('message')}")
        call("/reservation/release", {"id": soon_id, "reason": "用例结束释放"}, token=lab_admin)
    else:
        check("距开始不足 24h 发起调整 -> 409", False, f"无法创建用例：{body}")

    # >24h：用已通过的 res_id 换成 3 天后的空闲时段
    _, body = call("/reservation/get_info", {"id": res_id}, token=teacher)
    before = body.get("data") or {}
    new_day = next_weekday(6).isoformat()
    new_slot = find_free_lab_slot(teacher, lab_id, new_day)
    status, body = call("/reservation_change/create", {
        "reservationId": res_id, "changeType": 1, "reason": "调整到下周同一时段",
        "newReserveDate": new_day, "newSlotId": new_slot
    }, token=teacher)
    check("超过 24h 发起调整 -> 成功", status == 200, f"status={status} {body}")
    change_id = body.get("data")

    _, body = call("/reservation/get_info", {"id": res_id}, token=teacher)
    mid = body.get("data") or {}
    check("调整审批中：预约状态 CHANGE_PENDING", mid.get("status") == "CHANGE_PENDING", str(mid.get("status")))
    check("关键验证：业务字段未变（日期/时段不变）",
          mid.get("reserveDate") == before.get("reserveDate") and mid.get("slotId") == before.get("slotId"),
          f"{mid.get('reserveDate')} vs {before.get('reserveDate')}")
    check("详情里带 changePending", (mid.get("changePending") or {}).get("status") == 0, str(mid.get("changePending"))[:160])

    status, _ = call("/reservation_change/create",
                     {"reservationId": res_id, "changeType": 2, "reason": "重复申请"}, token=teacher)
    check("同一预约第二条待审申请 -> 409", status == 409, f"status={status}")

    status, _ = call("/reservation_change/audit", {"id": change_id, "result": 1, "opinion": "同意"}, token=lab_admin)
    check("审批通过 -> 200", status == 200, f"status={status}")
    _, body = call("/reservation/get_info", {"id": res_id}, token=teacher)
    after = body.get("data") or {}
    check("审批通过后日期已更新", after.get("reserveDate") == new_day, f"{after.get('reserveDate')} vs {new_day}")
    check("审批通过后状态回到 APPROVED", after.get("status") == "APPROVED", str(after.get("status")))

    # 驳回路径
    status, body = call("/reservation_change/create",
                        {"reservationId": res_id, "changeType": 2, "reason": "课程取消"}, token=teacher)
    change2 = body.get("data")
    status, _ = call("/reservation_change/audit", {"id": change2, "result": 2, "opinion": "不同意取消"}, token=lab_admin)
    _, body = call("/reservation/get_info", {"id": res_id}, token=teacher)
    check("调整驳回后预约回到 APPROVED 且日期未变",
          status == 200 and (body.get("data") or {}).get("status") == "APPROVED"
          and (body.get("data") or {}).get("reserveDate") == new_day,
          str(body.get("data"))[:160])

    # ---------- 8. 释放 ----------
    print("\n[8] 释放时段")
    status, _ = call("/reservation/release", {"id": res_id, "reason": "实验课临时取消"}, token=lab_admin)
    check("管理员释放 -> 200", status == 200, f"status={status}")
    _, body = call("/reservation/get_info", {"id": res_id}, token=teacher)
    check("释放后状态 CANCELED", (body.get("data") or {}).get("status") == "CANCELED", str(body.get("data"))[:120])
    _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=teacher)
    titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
    check("释放后教师收到站内信", any("释放" in t or "调整" in t for t in titles), str(titles))

    # ---------- 8.1 撤销待审核预约 -> 通知实验室管理员 ----------
    print("\n[8.1] 撤销待审核预约并通知管理员")
    day_cancel = next_weekday(9).isoformat()
    slot_cancel = find_free_lab_slot(teacher, 6, day_cancel)
    _, body = call("/laboratory/get_info", {"id": 6}, token=teacher)
    lab_cancel = body.get("data") or {}
    status, res_cancel = create_reservation(teacher, 6, day_cancel, slot_cancel,
                                            class_ids, lab_cancel.get("experimentTypeId"), "排队验收：撤单通知")
    if status != 200:
        check("创建待审核预约（撤单用例）", False, str(status))
    else:
        status, _ = call("/reservation/cancel", {"id": res_cancel, "reason": "课程调整"}, token=teacher)
        check("撤销待审核预约 -> 200", status == 200, f"status={status}")
        time.sleep(2)
        _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10}, token=lab_admin)
        admin_titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
        check("实验室管理员收到「预约已撤销」站内信", any("撤销" in t for t in admin_titles), str(admin_titles))

    # ---------- 8.2 预约过期扫描（APPROVED -> EXPIRED） ----------
    print("\n[8.2] 预约过期扫描")
    if not mysql_available():
        skip("过期扫描：APPROVED → EXPIRED", "docker/mysql 不可用")
    else:
        day_future = next_weekday(10).isoformat()
        slot_past = find_free_lab_slot(teacher, 7, day_future)
        _, body = call("/laboratory/get_info", {"id": 7}, token=teacher)
        lab_past = body.get("data") or {}
        status, res_past = create_reservation(teacher, 7, day_future, slot_past,
                                              class_ids, lab_past.get("experimentTypeId"), "排队验收：过期用例")
        if status != 200:
            skip("过期扫描：APPROVED → EXPIRED", f"无法创建用例：{status}")
        else:
            call("/reservation/audit", {"id": res_past, "result": "APPROVED"}, token=lab_admin)
            day_past = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
            ok, _ = run_sql(f"UPDATE tb_reservation SET reserve_date='{day_past}' WHERE id={res_past};")
            final_status = None
            waited = 0
            while waited < 25 and ok:
                time.sleep(5)
                waited += 5
                _, body = call("/reservation/get_info", {"id": res_past}, token=teacher)
                final_status = (body.get("data") or {}).get("status")
                if final_status == "EXPIRED":
                    break
            if final_status == "EXPIRED":
                check(f"过期扫描把 APPROVED 改为 EXPIRED（等待 {waited}s）", True)
            else:
                skip("过期扫描：APPROVED → EXPIRED",
                     "当前扫描间隔默认 1 小时；用 RESERVATION_EXPIRE_INTERVAL_MS=5000 "
                     "RESERVATION_EXPIRE_INITIAL_DELAY_MS=1000 重启后再跑"
                     f"（当前状态 {final_status}）")
            run_sql(f"DELETE FROM tb_reservation_change_request WHERE reservation_id={res_past}; "
                    f"DELETE FROM tb_reservation_class WHERE reservation_id={res_past}; "
                    f"DELETE FROM tb_reservation_equipment WHERE reservation_id={res_past}; "
                    f"DELETE FROM tb_reservation WHERE id={res_past};")

    # ---------- 8.3 调整申请里增减附带设备 ----------
    print("\n[8.3] 调整申请增减附带设备")
    day_eq = next_weekday(11).isoformat()
    slot_eq = find_free_lab_slot(teacher, 8, day_eq)
    _, body = call("/laboratory/get_info", {"id": 8}, token=teacher)
    lab_eq = body.get("data") or {}
    _, body = call("/equipment/list_option", {"labId": 8, "onlyAvailable": False}, token=teacher)
    lab_equipments = body.get("data") or []
    if not slot_eq or len(lab_equipments) < 2 or lab_eq.get("experimentTypeId") is None:
        skip("调整申请增减附带设备", "实验室 8 没有空闲时段或设备不足 2 台")
    else:
        eq_a, eq_b = lab_equipments[0], lab_equipments[1]
        status, res_eq = create_reservation(teacher, 8, day_eq, slot_eq, class_ids,
                                            lab_eq.get("experimentTypeId"), "排队验收：附带设备",
                                            equipments=[{"equipmentId": eq_a["id"], "equipmentCount": 1}])
        if status != 200:
            skip("调整申请增减附带设备", f"创建预约失败：{status}")
        else:
            call("/reservation/audit", {"id": res_eq, "result": "APPROVED"}, token=lab_admin)
            _, body = call("/reservation/get_info", {"id": res_eq}, token=teacher)
            before_eq = [item["equipmentId"] for item in (body.get("data") or {}).get("equipments") or []]
            check("预约带上了附带设备", eq_a["id"] in before_eq, str(before_eq))

            day_eq2 = next_weekday(12).isoformat()
            slot_eq2 = find_free_lab_slot(teacher, 8, day_eq2)
            status, body = call("/reservation_change/create", {
                "reservationId": res_eq, "changeType": 1, "reason": "换设备",
                "newReserveDate": day_eq2, "newSlotId": slot_eq2,
                "equipments": [{"equipmentId": eq_b["id"], "equipmentCount": 2}]
            }, token=teacher)
            change_eq_id = body.get("data")
            check("发起调整（改设备清单）", status == 200, f"status={status} {body}")
            if status == 200:
                status, _ = call("/reservation_change/audit",
                                 {"id": change_eq_id, "result": 1, "opinion": "同意换设备"}, token=lab_admin)
                _, body = call("/reservation/get_info", {"id": res_eq}, token=teacher)
                after_eq = [item["equipmentId"] for item in (body.get("data") or {}).get("equipments") or []]
                check("审批通过后设备清单被替换",
                      status == 200 and eq_b["id"] in after_eq and eq_a["id"] not in after_eq,
                      f"before={before_eq} after={after_eq}")
                check("新设备数量按申请写入",
                      any(item["equipmentId"] == eq_b["id"] and item["equipmentCount"] == 2
                          for item in (body.get("data") or {}).get("equipments") or []),
                      str((body.get("data") or {}).get("equipments")))
            call("/reservation/release", {"id": res_eq, "reason": "验收脚本清理"}, token=lab_admin)

    # ---------- 9. 并发 ----------
    print("\n[9] 并发判冲（20 线程抢同一实验室同一时段）")
    conc_day = next_weekday(8).isoformat()
    conc_slot = find_free_lab_slot(teacher, 3, conc_day)
    _, body = call("/laboratory/get_info", {"id": 3}, token=teacher)
    conc_lab = body.get("data") or {}
    conc_body = lab_submit_payload(3, conc_day, conc_slot, class_ids, conc_lab.get("experimentTypeId"))
    conc_results = []
    lock = threading.Lock()

    def worker():
        status, _ = call("/reservation/create", conc_body, token=teacher)
        with lock:
            conc_results.append(status)

    threads = [threading.Thread(target=worker) for _ in range(20)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    ok_count = conc_results.count(200)
    conflict_count = conc_results.count(409)
    check(f"20 并发只有 1 条成功（实际 {ok_count}）", ok_count == 1, str(conc_results))
    check(f"其余 {conflict_count} 条返回 409", conflict_count == 19, str(conc_results))

    _, body = call("/reservation/get_page",
                   {"pageNum": 1, "pageSize": 50, "labId": 3, "reserveDateBegin": conc_day,
                    "reserveDateEnd": conc_day, "status": "PENDING"}, token=lab_admin)
    rows = [item for item in (body.get("data") or {}).get("records") or [] if item["slotId"] == conc_slot]
    check("数据库中该时段只有 1 条占用中的预约", len(rows) == 1, f"count={len(rows)}")
    for row in rows:
        call("/reservation/release", {"id": row["id"], "reason": "并发用例清理"}, token=lab_admin)

    # ---------- 收尾 ----------
    if student_res.get("id"):
        call("/reservation/release", {"id": student_res["id"], "reason": "验收结束清理"}, token=lab_admin)

    print("\n" + "=" * 78)
    passed = sum(1 for _, ok, _ in results if ok)
    failed = [(n, d) for n, ok, d in results if not ok]
    print(f"共 {len(results)} 项：通过 {passed}，失败 {len(failed)}，跳过 {len(skipped)}")
    for name, detail in failed:
        print(f"  [FAIL] {name} -> {detail}")
    print("=" * 78)
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
