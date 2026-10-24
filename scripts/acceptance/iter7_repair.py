#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 7（设备运维）验收脚本。

覆盖：OSS 上传与三层校验（大小 / 后缀 / 文件头魔数）、报修提交与通知、
复核（确认 / 驳回）、派单（只能派维修人员）、维修进度与完成、
验收通过（归档 + 设备恢复正常）与不通过（重开）、越权拦截。

用法：
    python3 scripts/acceptance/iter7_repair.py

前置：
  1) ./dev.sh start
  2) .env 里已配置 OSS_* 且 bucket 可写（脚本会真实上传图片到 OSS）

脚本可重复执行：开跑前用 docker exec 清掉上次留下的测试工单，跑完把设备状态复原。
"""

import io
import json
import subprocess
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:8080/api"
results = []
skipped = []

# 1x1 透明 PNG（合法文件头）
PNG_BYTES = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000a49444154789c6300010000050001"
    "0d0a2db40000000049454e44ae426082"
)
MARK = "验收脚本用例"


def call(path, body=None, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body or {}).encode(), method="POST")
    req.add_header("Content-Type", "application/json;charset=UTF-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except Exception:
            return e.code, {"raw": payload[:200].decode("utf-8", "ignore")}


def upload(token, filename, content, content_type="image/png"):
    boundary = "----" + uuid.uuid4().hex
    body = io.BytesIO()
    body.write(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
               f"Content-Type: {content_type}\r\n\r\n".encode())
    body.write(content)
    body.write(b"\r\n")
    body.write(f'--{boundary}\r\nContent-Disposition: form-data; name="bizType"\r\n\r\nREPAIR\r\n'.encode())
    body.write(f"--{boundary}--\r\n".encode())
    req = urllib.request.Request(BASE + "/file/upload", data=body.getvalue(), method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except Exception:
            return e.code, {"raw": payload[:200].decode("utf-8", "ignore")}


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("  [OK] " if ok else "  [FAIL] ") + name + ("" if ok else f"  ->  {detail}"))


def skip(name, reason):
    skipped.append((name, reason))
    print(f"  [SKIP] {name}  ->  {reason}")


def login(username):
    _, body = call("/auth/login", {"username": username, "password": "123456"})
    return (body.get("data") or {}).get("accessToken")


def menu_titles(token):
    _, body = call("/auth/get_info", {}, token=token)
    titles = []
    for menu in (body.get("data") or {}).get("menus") or []:
        titles.append(menu["title"])
        for child in menu.get("children") or []:
            titles.append("  " + child["title"])
    return titles, (body.get("data") or {}).get("user") or {}


def mysql(sql):
    cmd = ["docker", "exec", "lab-reserve-mysql", "sh", "-c",
           f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "use lab_reserve; {sql}"']
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return proc.returncode == 0, (proc.stdout + proc.stderr).strip()
    except Exception as e:
        return False, str(e)


def cleanup():
    return mysql(
        "DELETE FROM tb_repair_record WHERE order_id IN "
        f"  (SELECT id FROM tb_repair_order WHERE fault_scene LIKE '{MARK}%'); "
        "DELETE FROM tb_repair_order WHERE fault_scene LIKE "
        f"  '{MARK}%';"
    )


def main():
    print("=" * 78)
    print("迭代 7 验收：设备运维（OSS 上传 / 报修 / 复核 / 派单 / 维修 / 验收）")
    print("=" * 78)

    ok, output = cleanup()
    if not ok:
        print(f"  [WARN] 清理上次测试工单失败（可能没有 docker）：{output[:120]}")

    student = login("stu2021001")
    admin = login("labadmin")
    m1 = login("repair01")
    m2 = login("repair02")
    check("学生 / 实验室管理员 / 两个维修人员登录", all([student, admin, m1, m2]))

    # ---------- 1. 菜单 ----------
    print("\n[1] 菜单与权限")
    stu_titles, _ = menu_titles(student)
    check("学生菜单含 设备运维/我要报修/我的报修",
          any(t == "设备运维" for t in stu_titles) and any("我要报修" in t for t in stu_titles)
          and any("我的报修" in t for t in stu_titles), str(stu_titles))
    check("学生没有 工单管理", not any("工单管理" in t for t in stu_titles), str(stu_titles))
    admin_titles, _ = menu_titles(admin)
    check("管理员菜单含 工单管理", any("工单管理" in t for t in admin_titles), str(admin_titles))
    m1_titles, _ = menu_titles(m1)
    check("维修人菜单含 我的维修任务", any("我的维修任务" in t for t in m1_titles), str(m1_titles))
    check("维修人不能调管理端工单分页 -> 403",
          call("/repair_order/get_page", {"pageNum": 1, "pageSize": 5}, token=m1)[0] == 403)

    # ---------- 2. 上传与三层校验 ----------
    print("\n[2] OSS 上传与校验")
    status, body = upload(student, "fault.png", PNG_BYTES)
    upload_ok = status == 200
    url = (body.get("data") or {}).get("url")
    if upload_ok:
        check("正常图片上传成功", True)
        try:
            with urllib.request.urlopen(url, timeout=20) as resp:
                check("上传后 URL 可直接访问（bucket 公共读）", resp.status == 200, f"status={resp.status}")
        except Exception as e:
            check("上传后 URL 可直接访问（bucket 公共读）", False, str(e))
    else:
        skip("正常图片上传成功", f"OSS 未配置或不可用：{body.get('message')}")
        skip("上传后 URL 可直接访问（bucket 公共读）", "依赖上传成功")

    status, body = upload(student, "big.png", PNG_BYTES + b"\0" * (6 * 1024 * 1024))
    check("6MB 图片 -> 400 超过 5MB", status == 400 and "5MB" in (body.get("message") or ""),
          f"status={status} {body.get('message')}")
    status, body = upload(student, "fake.png", b"this is a text file pretending to be png")
    check("改后缀的 txt -> 400 文件内容非法",
          status == 400 and "文件内容非法" in (body.get("message") or ""), f"status={status} {body.get('message')}")
    status, body = upload(student, "note.txt", PNG_BYTES)
    check(".txt 后缀 -> 400 仅支持图片", status == 400, f"status={status} {body.get('message')}")

    # ---------- 3. 报修 ----------
    print("\n[3] 提交报修")
    _, body = call("/equipment/list_option", {"labId": 1, "onlyAvailable": False}, token=student)
    equipment = (body.get("data") or [{}])[0]
    check("取到可报修设备", bool(equipment.get("id")), str(equipment)[:120])
    images = [url] if url else []
    status, body = call("/repair_order/create", {
        "equipmentId": equipment.get("id"),
        "faultDesc": "通电后屏幕无显示，风扇不转",
        "faultScene": MARK + "：上午实验课",
        "faultImages": images
    }, token=student)
    order = body.get("data") or {}
    order_id = order.get("id")
    check("学生提交报修 -> SUBMITTED", status == 200 and order.get("status") == "SUBMITTED", str(body)[:200])
    check("工单号格式 WX-YYYYMMDD-XXXX", str(order.get("orderNo", "")).startswith("WX-"), str(order.get("orderNo")))

    _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=admin)
    titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
    check("实验室管理员收到「新的报修工单」", any("新的报修工单" in t for t in titles), str(titles))

    # ---------- 4. 复核 ----------
    print("\n[4] 复核")
    status, _ = call("/repair_order/review", {"id": order_id, "result": "REJECTED"}, token=admin)
    check("驳回不填原因 -> 400", status == 400, f"status={status}")
    status, _ = call("/repair_order/review",
                     {"id": order_id, "result": "CONFIRMED", "priority": 3, "equipmentStatus": 2,
                      "opinion": "确认设备故障"}, token=admin)
    check("复核确认 + 优先级高 + 设备置维修中", status == 200, f"status={status}")
    _, body = call("/equipment/get_info", {"id": equipment.get("id")}, token=student)
    check("设备状态已变「维修中」", (body.get("data") or {}).get("statusName") == "维修中",
          str(body.get("data"))[:120])

    _, body = call("/reservation/check", {
        "resourceType": 2, "equipmentId": equipment.get("id"),
        "reserveDate": (__import__("datetime").date.today() + __import__("datetime").timedelta(days=3)).isoformat(),
        "slotId": 2, "usePeople": 1, "experimentTypeId": 1, "purpose": "维修中设备不可借"
    }, token=student)
    conflict = (body.get("data") or {}).get("conflict") or {}
    check("维修中的设备预约预检 -> EQUIPMENT_UNAVAILABLE", conflict.get("code") == "EQUIPMENT_UNAVAILABLE",
          str(body.get("data"))[:200])

    _, body = call("/repair_order/get_info", {"id": order_id}, token=student)
    check("工单优先级变「高」", (body.get("data") or {}).get("priorityName") == "高", str(body.get("data"))[:120])

    # ---------- 5. 派单 ----------
    print("\n[5] 派单")
    _, body = call("/user/list_maintainer", {}, token=admin)
    maintainers = body.get("data") or []
    check("维修人员下拉可用", len(maintainers) >= 2, str(maintainers)[:160])
    m1_id = next((item["id"] for item in maintainers if item["id"] == (menu_titles(m1)[1] or {}).get("id")), None)
    if m1_id is None:
        m1_id = maintainers[0]["id"]
    status, _ = call("/repair_order/assign", {"id": order_id, "assigneeId": m1_id}, token=m1)
    check("维修人调派单 -> 403", status == 403, f"status={status}")
    status, _ = call("/repair_order/assign", {"id": order_id, "assigneeId": m1_id}, token=admin)
    check("管理员派单 -> 200", status == 200, f"status={status}")

    _, body = call("/repair_order/get_my_assigned_page", {"pageNum": 1, "pageSize": 20}, token=m1)
    assigned_ids = [item["id"] for item in (body.get("data") or {}).get("records") or []]
    check("维修人「我的维修任务」能看到该工单", order_id in assigned_ids, str(assigned_ids[:10]))
    _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=m1)
    titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
    check("维修人收到派单通知", any("新的维修任务" in t for t in titles), str(titles))

    # ---------- 6. 越权 ----------
    print("\n[6] 越权拦截")
    status, _ = call("/repair_order/update_progress", {"id": order_id, "repairDesc": "不是我的工单"}, token=m2)
    check("维修人 B 操作指派给 A 的工单 -> 403", status == 403, f"status={status}")

    # ---------- 7. 维修 ----------
    print("\n[7] 维修进度与完成")
    status, _ = call("/repair_order/update_progress",
                     {"id": order_id, "repairDesc": "已定位为主板电容损坏，等待配件"}, token=m1)
    check("维修人更新进度 -> 200", status == 200, f"status={status}")
    _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=student)
    titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
    check("报修人收到「维修进度更新」", any("维修进度更新" in t for t in titles), str(titles))
    status, _ = call("/repair_order/complete", {"id": order_id}, token=m1)
    check("完成维修不填维修说明 -> 400", status == 400, f"status={status}")
    status, _ = call("/repair_order/complete",
                     {"id": order_id, "repairDesc": "更换主板电容 2 个，通电测试正常",
                      "partsReplaced": "电容 25V/470uF × 2"}, token=m1)
    check("维修人完成维修 -> 200", status == 200, f"status={status}")
    _, body = call("/repair_order/get_info", {"id": order_id}, token=admin)
    detail = body.get("data") or {}
    check("工单状态变「待验收」", detail.get("status") == "REPAIRED", str(detail.get("status")))
    check("维修时间线有 2 条记录", len(detail.get("records") or []) == 2, str(len(detail.get("records") or [])))
    check("故障图片可回显", len(detail.get("faultImages") or []) >= 1, str(detail.get("faultImages"))[:120])

    # ---------- 8. 验收通过 ----------
    print("\n[8] 验收通过")
    status, _ = call("/repair_order/accept", {"id": order_id, "result": "PASS", "opinion": "测试正常"}, token=admin)
    check("验收通过 -> 200", status == 200, f"status={status}")
    _, body = call("/repair_order/get_info", {"id": order_id}, token=admin)
    detail = body.get("data") or {}
    check("工单变 ACCEPTED 且写归档时间",
          detail.get("status") == "ACCEPTED" and bool(detail.get("archiveTime")), str(detail)[:200])
    _, body = call("/equipment/get_info", {"id": equipment.get("id")}, token=student)
    check("设备状态恢复「正常」", (body.get("data") or {}).get("statusName") == "正常",
          str(body.get("data"))[:120])

    # ---------- 9. 验收不通过 ----------
    print("\n[9] 验收不通过（重开）")
    status, body = call("/repair_order/create", {
        "equipmentId": equipment.get("id"), "faultDesc": "风扇异响",
        "faultScene": MARK + "：重开用例", "faultImages": images
    }, token=student)
    order2 = (body.get("data") or {}).get("id")
    call("/repair_order/review", {"id": order2, "result": "CONFIRMED", "priority": 2, "equipmentStatus": 2}, token=admin)
    call("/repair_order/assign", {"id": order2, "assigneeId": m1_id}, token=admin)
    call("/repair_order/update_progress", {"id": order2, "repairDesc": "已清理风扇"}, token=m1)
    call("/repair_order/complete", {"id": order2, "repairDesc": "已清理风扇并加润滑油"}, token=m1)
    status, _ = call("/repair_order/accept", {"id": order2, "result": "FAIL", "opinion": "仍有异响，请复查"}, token=admin)
    check("验收不通过 -> 200", status == 200, f"status={status}")
    _, body = call("/repair_order/get_info", {"id": order2}, token=admin)
    check("工单变 REOPENED", (body.get("data") or {}).get("status") == "REOPENED", str((body.get("data") or {}).get("status")))
    status, _ = call("/repair_order/update_progress", {"id": order2, "repairDesc": "重新拆检"}, token=m1)
    check("重开后可再次更新进度", status == 200, f"status={status}")

    # ---------- 10. 驳回路径 ----------
    print("\n[10] 复核驳回")
    status, body = call("/repair_order/create", {
        "equipmentId": equipment.get("id"), "faultDesc": "键盘按键失灵",
        "faultScene": MARK + "：驳回用例", "faultImages": []
    }, token=student)
    order3 = (body.get("data") or {}).get("id")
    status, _ = call("/repair_order/review",
                     {"id": order3, "result": "REJECTED", "opinion": "非设备故障，属操作失误"}, token=admin)
    check("复核驳回 -> 200", status == 200, f"status={status}")
    _, body = call("/repair_order/get_info", {"id": order3}, token=student)
    check("工单状态 REJECTED", (body.get("data") or {}).get("status") == "REJECTED",
          str((body.get("data") or {}).get("status")))
    _, body = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=student)
    titles = [item["title"] for item in (body.get("data") or {}).get("records") or []]
    check("报修人收到驳回通知", any("驳回" in t for t in titles), str(titles))

    # ---------- 收尾：设备状态复原 ----------
    call("/equipment/update_status",
         {"id": equipment.get("id"), "status": 1, "updateReason": "验收脚本收尾：恢复设备正常"}, token=admin)

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
