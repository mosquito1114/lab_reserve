#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 4（资源管理）验收脚本。

覆盖：字典（实验类型 / 时段）、实验室、设备、资源变更留痕、可用时段与库存。

用法：
  1) ./dev.sh start
  2) python3 scripts/acceptance/iter4_resource.py

说明：
  - 脚本可重复执行：固定编号 LAB-99 / ZY-99-001 / 字典编码 TEST_TYPE / S9 会被复用而不是重复创建。
  - 「库存扣减」需要一个占用中的预约做夹具，脚本会用 docker exec 直接往 tb_reservation
    插一条测试数据，验证完立即删除；没有 docker 时这两项标记 SKIP。
  - 仅用标准库（urllib + subprocess）。
"""

import datetime
import json
import subprocess
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
results = []
skipped = []

LAB_NO = "LAB-99"
EQUIP_NO = "ZY-99-001"
TYPE_CODE = "TEST_TYPE"
SLOT_CODE = "S9"
LAB_NAME = "验收实验室"


def call(path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body or {}).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
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


def mysql(sql):
    """执行一条 SQL（用于造/清测试夹具）。返回 (ok, 输出)。"""
    cmd = ["docker", "exec", "lab-reserve-mysql", "sh", "-c",
           f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "use lab_reserve; {sql}"']
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return proc.returncode == 0, (proc.stdout + proc.stderr).strip()
    except Exception as e:  # docker 不可用
        return False, str(e)


def date_after(days):
    return (datetime.date.today() + datetime.timedelta(days=days)).isoformat()


def main():
    print("=" * 78)
    print("迭代 4 验收：资源管理（字典 / 实验室 / 设备 / 留痕 / 可用性）")
    print("=" * 78)

    # ---------- 0. 登录 ----------
    print("\n[0] 准备：登录")
    lab_admin = login("labadmin")
    admin = login("admin")
    student = login("stu2021001")
    teacher = login("tea1001")
    check("labadmin / admin / student / teacher 登录",
          all([lab_admin, admin, student, teacher]))

    # ---------- 1. 菜单与权限 ----------
    print("\n[1] 菜单与权限")
    lab_titles, lab_perms = menus_of(lab_admin)
    for title in ["资源管理", "  实验室管理", "  设备管理", "  字典维护", "  资源更新日志", "  资源可用性"]:
        check(f"LAB_ADMIN 菜单含「{title.strip()}」", any(t == title for t in lab_titles), str(lab_titles))

    stu_titles, stu_perms = menus_of(student)
    check("STUDENT 菜单含 实验室管理", any("实验室管理" in t for t in stu_titles), str(stu_titles))
    check("STUDENT 菜单不含 字典维护", not any("字典维护" in t for t in stu_titles), str(stu_titles))
    check("STUDENT 菜单不含 资源更新日志", not any("资源更新日志" in t for t in stu_titles), str(stu_titles))

    lab_body = {"labNo": "LAB-97", "name": "越权实验室", "location": "X", "openTimeStart": "08:00",
                "openTimeEnd": "18:00", "openWeekdays": "1,2,3", "capacity": 10,
                "experimentTypeId": 1, "updateReason": "越权测试"}
    check("STUDENT 新增实验室 -> 403", call("/laboratory/create", lab_body, token=student)[0] == 403)
    check("STUDENT 改字典 -> 403", call("/experiment_type/create", {"code": "X", "name": "X"}, token=student)[0] == 403)
    check("STUDENT 看资源日志 -> 403",
          call("/resource_log/get_page", {"pageNum": 1, "pageSize": 10}, token=student)[0] == 403)
    check("TEACHER 看资源日志 -> 403",
          call("/resource_log/get_page", {"pageNum": 1, "pageSize": 10}, token=teacher)[0] == 403)
    check("LAB_ADMIN 看资源日志 -> 200",
          call("/resource_log/get_page", {"pageNum": 1, "pageSize": 10}, token=lab_admin)[0] == 200)
    check("STUDENT 可看实验室列表", call("/laboratory/get_page", {"pageNum": 1, "pageSize": 5}, token=student)[0] == 200)
    check("STUDENT 可看设备列表", call("/equipment/get_page", {"pageNum": 1, "pageSize": 5}, token=student)[0] == 200)

    # ---------- 2. 字典 ----------
    print("\n[2] 字典：实验类型 / 时段")
    status, body = call("/experiment_type/get_list", {}, token=lab_admin)
    types = body.get("data") or []
    check("实验类型列表可用", status == 200 and len(types) >= 5, str(types)[:200])
    type_id = None
    for item in types:
        if item["code"] == TYPE_CODE:
            type_id = item["id"]
    if type_id is None:
        status, body = call("/experiment_type/create",
                            {"code": TYPE_CODE, "name": "验收类型", "sort": 99, "status": 1}, token=lab_admin)
        type_id = body.get("data")
        check("新增实验类型", status == 200 and isinstance(type_id, int), f"status={status} {body}")
    else:
        check("新增实验类型", True)

    status, body = call("/experiment_type/create", {"code": TYPE_CODE, "name": "重复类型"}, token=lab_admin)
    check("实验类型编码重复 -> 409", status == 409, f"status={status} {body}")
    check("实验类型更新 -> 200",
          call("/experiment_type/update", {"id": type_id, "code": TYPE_CODE, "name": "验收类型2", "sort": 99,
                                           "status": 1}, token=lab_admin)[0] == 200)
    status, body = call("/experiment_type/delete", {"id": 2}, token=lab_admin)
    check("被实验室引用的实验类型删除 -> 409", status == 409, f"status={status} {body}")

    status, body = call("/time_slot/get_list", {}, token=lab_admin)
    slots = body.get("data") or []
    check("时段列表可用", status == 200 and len(slots) >= 5, str(slots)[:200])
    slot_id = None
    for item in slots:
        if item["code"] == SLOT_CODE:
            slot_id = item["id"]
    if slot_id is None:
        status, body = call("/time_slot/create",
                            {"code": SLOT_CODE, "name": "11-12节", "startTime": "21:00", "endTime": "22:40",
                             "sort": 99, "status": 1}, token=lab_admin)
        slot_id = body.get("data")
        check("新增时段", status == 200 and isinstance(slot_id, int), f"status={status} {body}")
    else:
        check("新增时段", True)

    check("时段时间倒置 -> 400",
          call("/time_slot/create", {"code": "S99", "name": "x", "startTime": "20:00", "endTime": "19:00"},
               token=lab_admin)[0] == 400)
    check("时段编码重复 -> 409",
          call("/time_slot/create", {"code": SLOT_CODE, "name": "x", "startTime": "21:00", "endTime": "22:40"},
               token=lab_admin)[0] == 409)
    status, body = call("/time_slot/delete", {"id": 1}, token=lab_admin)
    check("被预约引用的时段删除 -> 409", status == 409, f"status={status} {body}")
    check("清理验收时段 -> 200", call("/time_slot/delete", {"id": slot_id}, token=lab_admin)[0] == 200)

    # ---------- 3. 实验室 ----------
    print("\n[3] 实验室：校验 / 留痕 / 状态")
    base_lab = {
        "labNo": LAB_NO, "name": LAB_NAME, "location": "实验楼 Z101",
        "openTimeStart": "08:00", "openTimeEnd": "21:00", "openWeekdays": "1,2,3,4,5",
        "capacity": 60, "experimentTypeId": type_id, "description": "验收用", "updateReason": "验收新增"
    }
    status, body = call("/laboratory/get_page", {"pageNum": 1, "pageSize": 20, "keyword": LAB_NO}, token=lab_admin)
    existing = (body.get("data") or {}).get("records") or []
    if existing:
        lab_id = existing[0]["id"]
        check("新增实验室（已存在则复用）", True)
    else:
        status, body = call("/laboratory/create", base_lab, token=lab_admin)
        lab_id = body.get("data")
        check("新增实验室", status == 200 and isinstance(lab_id, int), f"status={status} {body}")

    status, body = call("/laboratory/create", base_lab, token=lab_admin)
    check("实验室编号重复 -> 409", status == 409, f"status={status} {body}")
    bad = dict(base_lab, labNo="LAB-1")
    check("实验室编号格式错误 -> 400", call("/laboratory/create", bad, token=lab_admin)[0] == 400)
    no_reason = dict(base_lab, labNo="LAB-98")
    no_reason.pop("updateReason")
    check("缺更新原因 -> 400", call("/laboratory/create", no_reason, token=lab_admin)[0] == 400)
    bad_week = dict(base_lab, labNo="LAB-98", openWeekdays="1,8")
    check("开放星期非法 -> 400", call("/laboratory/create", bad_week, token=lab_admin)[0] == 400)
    bad_time = dict(base_lab, labNo="LAB-98", openTimeStart="20:00", openTimeEnd="09:00")
    check("开放时间倒置 -> 400", call("/laboratory/create", bad_time, token=lab_admin)[0] == 400)

    # 把状态拉回可用 + 容量复位，保证后续断言从稳定状态开始
    status, body = call("/laboratory/get_info", {"id": lab_id}, token=lab_admin)
    detail = body.get("data") or {}
    if detail.get("status") == 0:
        call("/laboratory/update_status", {"id": lab_id, "status": 1, "updateReason": "验收前恢复启用"}, token=lab_admin)
    call("/laboratory/update", dict(base_lab, id=lab_id, capacity=60, updateReason="验收复位容量60"), token=lab_admin)

    status, body = call("/laboratory/update", dict(base_lab, id=lab_id, capacity=80, updateReason="扩容到80"), token=lab_admin)
    check("修改容量 60 -> 80", status == 200, f"status={status} {body}")

    status, body = call("/resource_log/get_page",
                        {"pageNum": 1, "pageSize": 5, "resourceType": 1, "resourceId": lab_id}, token=lab_admin)
    logs = (body.get("data") or {}).get("records") or []
    check("资源更新日志有记录", status == 200 and len(logs) >= 1, str(logs)[:200])
    latest = logs[0] if logs else {}
    before = json.loads(latest.get("beforeJson") or "{}")
    after = json.loads(latest.get("afterJson") or "{}")
    check("快照 before.capacity = 60", before.get("capacity") == 60, str(before))
    check("快照 after.capacity = 80", after.get("capacity") == 80, str(after))
    check("快照记录了更新原因", latest.get("updateReason") == "扩容到80", str(latest)[:200])

    check("停用实验室 -> 200",
          call("/laboratory/update_status", {"id": lab_id, "status": 0, "updateReason": "验收停用"}, token=lab_admin)[0] == 200)
    check("重复停用 -> 409",
          call("/laboratory/update_status", {"id": lab_id, "status": 0, "updateReason": "验收停用"}, token=lab_admin)[0] == 409)
    status, body = call("/laboratory/list_option", {}, token=lab_admin)
    check("下拉不含停用实验室", all(item["id"] != lab_id for item in body.get("data") or []), str(body.get("data"))[:200])

    status, body = call("/resource_log/get_page",
                        {"pageNum": 1, "pageSize": 5, "resourceType": 1, "resourceId": lab_id, "operation": 3},
                        token=lab_admin)
    status_logs = (body.get("data") or {}).get("records") or []
    check("状态变更也留痕（operation=3）", status == 200 and len(status_logs) >= 1, str(status_logs)[:200])

    # ---------- 4. 设备 ----------
    print("\n[4] 设备：校验 / 留痕 / 状态")
    base_equip = {"resourceNo": EQUIP_NO, "name": "验收设备", "model": "AC-001", "stock": 5,
                  "labId": lab_id, "nextCalibrationDate": "2026-12-31", "remark": "验收用", "updateReason": "验收新增"}
    status, body = call("/equipment/get_page", {"pageNum": 1, "pageSize": 20, "resourceNo": EQUIP_NO}, token=lab_admin)
    existing = (body.get("data") or {}).get("records") or []
    if existing:
        equip_id = existing[0]["id"]
        check("新增设备（已存在则复用）", True)
    else:
        status, body = call("/equipment/create", base_equip, token=lab_admin)
        equip_id = body.get("data")
        check("新增设备", status == 200 and isinstance(equip_id, int), f"status={status} {body}")

    check("设备编号格式错误 -> 400",
          call("/equipment/create", dict(base_equip, resourceNo="XX-01-001"), token=lab_admin)[0] == 400)
    check("设备编号重复 -> 409", call("/equipment/create", base_equip, token=lab_admin)[0] == 409)
    no_reason = dict(base_equip, resourceNo="ZY-99-002")
    no_reason.pop("updateReason")
    check("设备缺更新原因 -> 400", call("/equipment/create", no_reason, token=lab_admin)[0] == 400)

    status, body = call("/equipment/update_status", {"id": equip_id, "status": 2, "updateReason": "验收送修"}, token=lab_admin)
    check("设备状态改为维修中", status == 200, f"status={status} {body}")
    status, body = call("/equipment/get_info", {"id": equip_id}, token=lab_admin)
    check("设备状态已生效（statusName=维修中）", (body.get("data") or {}).get("statusName") == "维修中",
          str(body.get("data"))[:200])
    call("/equipment/update_status", {"id": equip_id, "status": 1, "updateReason": "验收修好"}, token=lab_admin)

    status, body = call("/resource_log/get_page",
                        {"pageNum": 1, "pageSize": 5, "resourceType": 2, "resourceId": equip_id}, token=lab_admin)
    equip_logs = (body.get("data") or {}).get("records") or []
    check("设备变更也有留痕", status == 200 and len(equip_logs) >= 2, str(equip_logs)[:200])
    check("设备日志带资源编号", all(item.get("resourceNo") == EQUIP_NO for item in equip_logs), str(equip_logs)[:200])

    # ---------- 5. 可用性 ----------
    print("\n[5] 可用时段 / 库存可用量")
    tomorrow = date_after(1)
    status, body = call("/laboratory/get_availability", {"id": lab_id, "date": tomorrow}, token=lab_admin)
    data = body.get("data") or {}
    check("实验室可用时段接口可用", status == 200 and len(data.get("slots") or []) >= 1, str(data)[:200])
    check("停用实验室的时段不可用且给出原因",
          all(not slot["available"] and slot["reason"] == "实验室已停用" for slot in data.get("slots") or []),
          str(data)[:300])

    call("/laboratory/update_status", {"id": lab_id, "status": 1, "updateReason": "验收恢复启用"}, token=lab_admin)
    status, body = call("/laboratory/get_availability", {"id": lab_id, "date": tomorrow}, token=lab_admin)
    data = body.get("data") or {}
    weekday = datetime.date.fromisoformat(tomorrow).isoweekday()
    if weekday <= 5:
        check("启用后工作日时段可预约", all(slot["available"] for slot in data.get("slots") or []), str(data)[:300])
    else:
        check("启用后非开放星期不可预约",
              all(not slot["available"] for slot in data.get("slots") or []), str(data)[:300])

    # 星期判定：挑一个不在 openWeekdays 里的日期
    closed_date = None
    for offset in range(1, 8):
        candidate = datetime.date.today() + datetime.timedelta(days=offset)
        if candidate.isoweekday() > 5:
            closed_date = candidate.isoformat()
            break
    if closed_date:
        status, body = call("/laboratory/get_availability", {"id": lab_id, "date": closed_date}, token=lab_admin)
        data = body.get("data") or {}
        check("非开放星期的时段全部不可用",
              data.get("openWeekday") is False
              and all(not slot["available"] for slot in data.get("slots") or []), str(data)[:300])

    status, body = call("/equipment/get_availability", {"id": equip_id, "date": tomorrow}, token=lab_admin)
    data = body.get("data") or {}
    slots = data.get("slots") or []
    check("设备可用量接口可用", status == 200 and len(slots) >= 1, str(data)[:200])
    check("无占用时 remainCount = stock",
          all(slot["usedCount"] == 0 and slot["remainCount"] == data.get("stock") and slot["available"]
              for slot in slots), str(slots)[:300])

    # 造一条占用中的预约，验证库存扣减后立即清掉
    fixture_slot = slots[0]["slotId"] if slots else None
    ok, output = mysql(
        "DELETE FROM tb_reservation WHERE reserve_no = 'R-TEST-ITER4'; "
        "INSERT INTO tb_reservation (reserve_no, applicant_id, resource_type, lab_id, equipment_id, "
        "equipment_count, reserve_date, slot_id, use_people, experiment_type_id, purpose, status) VALUES "
        f"('R-TEST-ITER4', 5, 2, {lab_id}, {equip_id}, 2, '{tomorrow}', {fixture_slot}, 2, "
        f"{type_id}, '迭代4验收夹具', 'APPROVED');"
    )
    if ok and fixture_slot:
        status, body = call("/equipment/get_availability", {"id": equip_id, "date": tomorrow}, token=lab_admin)
        target = next((slot for slot in (body.get("data") or {}).get("slots") or []
                       if slot["slotId"] == fixture_slot), None)
        check("占用 2 台后 remainCount = stock - 2",
              target is not None and target["usedCount"] == 2
              and target["remainCount"] == (body.get("data") or {}).get("stock") - 2,
              str(target))
        check("remainCount + usedCount = stock",
              target is not None and target["remainCount"] + target["usedCount"] == (body.get("data") or {}).get("stock"),
              str(target))
        mysql("DELETE FROM tb_reservation WHERE reserve_no = 'R-TEST-ITER4';")
    else:
        skip("占用 2 台后 remainCount = stock - 2", f"无法造夹具：{output[:120]}")
        skip("remainCount + usedCount = stock", f"无法造夹具：{output[:120]}")

    # ---------- 6. 资源日志筛选 ----------
    print("\n[6] 资源日志筛选")
    status, body = call("/resource_log/get_page", {"pageNum": 1, "pageSize": 10, "resourceType": 2}, token=lab_admin)
    check("按资源类型筛选（设备）",
          status == 200 and all(item["resourceType"] == 2 for item in (body.get("data") or {}).get("records") or []),
          str(body.get("data"))[:200])
    status, body = call("/resource_log/get_page", {"pageNum": 1, "pageSize": 10, "operation": 1}, token=lab_admin)
    check("按操作筛选（新增）",
          status == 200 and all(item["operation"] == 1 for item in (body.get("data") or {}).get("records") or []),
          str(body.get("data"))[:200])
    status, body = call("/resource_log/get_page",
                        {"pageNum": 1, "pageSize": 10, "beginTime": "2000-01-01 00:00:00",
                         "endTime": "2099-12-31 23:59:59"}, token=lab_admin)
    check("按时间范围筛选", status == 200 and (body.get("data") or {}).get("total", 0) >= 1, str(body.get("data"))[:200])

    # ---------- 收尾 ----------
    # 把验收实验室停用，避免它出现在预约下拉里干扰后续演示
    call("/laboratory/update_status", {"id": lab_id, "status": 0, "updateReason": "验收结束停用"}, token=lab_admin)

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
