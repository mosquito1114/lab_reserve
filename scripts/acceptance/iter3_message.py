#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 3（消息通知）验收脚本。

覆盖：站内信收件箱 / 未读数 / 已读 / 删除 / 清空 / 权限 / 邮件投递状态 / 邮件真实发送。

用法：
  1) ./dev.sh start（数据库 + 后端 + 前端）
  2) 如需验证真实发信，二选一：
       - 配好 QQ 邮箱授权码（.env 的 MAIL_USERNAME / MAIL_PASSWORD），或
       - 起本地调试 SMTP 并用它启动后端（见 scripts/tools/dev_smtp.py 头部说明）
  3) python3 scripts/acceptance/iter3_message.py

脚本可重复执行；「邮件真发」相关断言在 SMTP 未配置时标记为 SKIP，不算失败。
仅用标准库（urllib）。
"""

import json
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
results = []
skipped = []

STUDENT = "stu2021001"


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


def login(username, password="123456"):
    status, body = call("/auth/login", {"username": username, "password": password})
    token = (body.get("data") or {}).get("accessToken")
    return status, body, token


def menu_titles(token):
    _, body = call("/auth/get_info", {}, token=token)
    data = body.get("data") or {}
    titles = []
    for menu in data.get("menus") or []:
        titles.append(menu["title"])
        for child in menu.get("children") or []:
            titles.append("  " + child["title"])
    return titles, (data.get("permissions") or [])


def unread(token):
    _, body = call("/message/get_unread_count", {}, token=token)
    return ((body.get("data") or {}).get("count"))


def wait_email_state(token, message_id, expect="OK", timeout=40):
    """轮询投递记录，直到指定消息的 emailState 命中期望值。"""
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        _, body = call("/message/get_delivery_log_page", {"pageNum": 1, "pageSize": 100}, token=token)
        for row in (body.get("data") or {}).get("records") or []:
            if row.get("messageId") == message_id:
                last = row
                if row.get("emailState") == expect:
                    return True, row
        time.sleep(2)
    return False, last


def main():
    print("=" * 78)
    print("迭代 3 验收：消息通知（站内信 + 邮件投递）")
    print("=" * 78)

    # ---------- 0. 登录 ----------
    print("\n[0] 准备：登录各角色")
    status, body, admin = login("admin")
    check("admin 登录", status == 200 and bool(admin), f"status={status}")
    _, _, labadmin = login("labadmin")
    _, _, student = login(STUDENT)
    _, _, teacher = login("tea1001")
    _, _, maintainer = login("repair01")
    check("学生 / 教师 / 维修人员登录", all([student, teacher, maintainer]))

    _, stu_profile = call("/user/get_self_profile", {}, token=student)
    stu_id = (stu_profile.get("data") or {}).get("id")
    check("拿到学生 id", isinstance(stu_id, int), str(stu_profile)[:200])

    # ---------- 1. 权限与菜单 ----------
    print("\n[1] 权限与菜单")
    titles, admin_perms = menu_titles(admin)
    check("admin 拥有 message:send", "message:send" in admin_perms, str(admin_perms)[:200])
    check("admin 菜单含 消息中心", any("消息中心" in t for t in titles), str(titles))
    check("admin 菜单含 邮件投递记录", any("邮件投递记录" in t for t in titles), str(titles))

    for role, token in [("学生", student), ("教师", teacher), ("实验室管理员", labadmin), ("维修人员", maintainer)]:
        role_titles, role_perms = menu_titles(token)
        check(f"{role} 菜单含 消息中心", any("消息中心" in t for t in role_titles), str(role_titles))
        check(f"{role} 菜单不含 邮件投递记录", not any("邮件投递记录" in t for t in role_titles), str(role_titles))
        check(f"{role} 无 message:send", "message:send" not in role_perms, str(role_perms)[:200])

    # ---------- 2. 收件箱权限 ----------
    print("\n[2] 接口权限")
    access = {
        "学生 收件箱": call("/message/get_page", {}, token=student)[0],
        "教师 收件箱": call("/message/get_page", {}, token=teacher)[0],
        "维修人员 收件箱": call("/message/get_page", {}, token=maintainer)[0],
        "学生 投递记录": call("/message/get_delivery_log_page", {}, token=student)[0],
        "实验室管理员 投递记录": call("/message/get_delivery_log_page", {}, token=labadmin)[0],
        "管理员 投递记录": call("/message/get_delivery_log_page", {}, token=admin)[0],
        "学生 发通知": call("/message/send", {"receiverIds": [stu_id], "title": "x", "content": "y"}, token=student)[0],
        "教师 发通知": call("/message/send", {"receiverIds": [stu_id], "title": "x", "content": "y"}, token=teacher)[0],
    }
    check("学生 / 教师 / 维修人员 可看自己的收件箱", all(access[k] == 200 for k in
          ["学生 收件箱", "教师 收件箱", "维修人员 收件箱"]), str(access))
    check("非系统管理员看投递记录 -> 403", access["学生 投递记录"] == 403
          and access["实验室管理员 投递记录"] == 403, str(access))
    check("系统管理员看投递记录 -> 200", access["管理员 投递记录"] == 200, str(access))
    check("非系统管理员发通知 -> 403", access["学生 发通知"] == 403 and access["教师 发通知"] == 403, str(access))

    # ---------- 3. 发通知 + 参数校验 ----------
    print("\n[3] 发送系统通知与参数校验")
    status, body = call("/message/send", {"receiverIds": [], "title": "x", "content": "y"}, token=admin)
    check("空接收人 -> 400", status == 400, f"status={status} {body}")
    status, body = call("/message/send", {"receiverIds": [stu_id], "title": "", "content": "y"}, token=admin)
    check("空标题 -> 400", status == 400, f"status={status}")
    status, body = call("/message/send", {"receiverIds": [stu_id], "title": "t" * 101, "content": "y"}, token=admin)
    check("标题超 100 字 -> 400", status == 400, f"status={status}")

    # 先清空，保证计数断言干净
    call("/message/clear", {}, token=student)
    check("清空后未读数为 0", unread(student) == 0, str(unread(student)))

    sent_ids = []
    for i in range(3):
        status, body = call("/message/send", {
            "receiverIds": [stu_id],
            "title": f"迭代3验收通知{i + 1}",
            "content": f"这是第 {i + 1} 条验收通知，用于验证收件箱、未读数与邮件投递。"
        }, token=admin)
        check(f"第 {i + 1} 条通知发送成功", status == 200, f"status={status} {body}")

    # 重复接收人应去重
    status, body = call("/message/send", {"receiverIds": [stu_id, stu_id], "title": "去重通知", "content": "同一人只应收到一条"},
                        token=admin)
    check("重复接收人去重（返回 1）", status == 200 and body.get("data") == 1, f"status={status} {body}")

    # ---------- 4. 收件箱与未读数 ----------
    print("\n[4] 收件箱、未读数、筛选")
    check("未读数为 4", unread(student) == 4, str(unread(student)))

    status, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10}, token=student)
    page = body.get("data") or {}
    records = page.get("records") or []
    check("收件箱 total = 4", page.get("total") == 4, json.dumps(page, ensure_ascii=False)[:200])
    check("收件箱按时间倒序", records and records[0]["title"] == "去重通知",
          json.dumps([r["title"] for r in records], ensure_ascii=False))
    check("列表返回 typeName", all(r.get("typeName") for r in records), str(records[:1]))

    status, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10, "isRead": 0}, token=student)
    check("筛未读命中 4 条", (body.get("data") or {}).get("total") == 4, json.dumps(body.get("data"), ensure_ascii=False)[:200])

    status, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10, "type": "SYSTEM"}, token=student)
    check("按 type=SYSTEM 筛选命中 4 条", (body.get("data") or {}).get("total") == 4, str(body.get("data"))[:200])

    status, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10, "type": "REPAIR"}, token=student)
    check("按 type=REPAIR 筛选命中 0 条", (body.get("data") or {}).get("total") == 0, str(body.get("data"))[:200])

    # ---------- 5. 详情 / 已读 ----------
    print("\n[5] 详情自动已读 / 单条已读 / 全部已读")
    target = records[0]["id"]
    other = records[1]["id"]

    status, body = call("/message/get_info", {"id": target}, token=student)
    detail = body.get("data") or {}
    check("详情返回正文", status == 200 and "同一人只应收到一条" in (detail.get("content") or ""), str(detail)[:200])
    check("详情自动置已读", detail.get("isRead") == 1, str(detail.get("isRead")))
    check("已读后未读数 -1", unread(student) == 3, str(unread(student)))

    status, body = call("/message/get_info", {"id": target}, token=student)
    check("重复打开同一条不重复扣减", unread(student) == 3, str(unread(student)))

    status, body = call("/message/read", {"id": other}, token=student)
    check("单条已读 -> 200 且未读数 2", status == 200 and unread(student) == 2, f"status={status} unread={unread(student)}")

    status, _ = call("/message/get_info", {"id": target}, token=teacher)
    check("教师读学生消息 -> 403", status == 403, f"status={status}")

    status, _ = call("/message/read_all", {}, token=student)
    check("全部已读 -> 未读数 0", status == 200 and unread(student) == 0, f"status={status} unread={unread(student)}")

    status, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10, "isRead": 1}, token=student)
    check("全部已读后筛已读命中 4 条", (body.get("data") or {}).get("total") == 4, str(body.get("data"))[:200])

    # ---------- 6. 删除 / 清空 ----------
    print("\n[6] 删除与一键清空")
    status, _ = call("/message/delete", {"id": target}, token=student)
    check("删除单条 -> 200", status == 200, f"status={status}")
    status, _ = call("/message/get_info", {"id": target}, token=student)
    check("删除后详情 -> 404", status == 404, f"status={status}")
    status, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10}, token=student)
    check("删除后 total = 3", (body.get("data") or {}).get("total") == 3, str(body.get("data"))[:200])

    status, _ = call("/message/clear", {}, token=student)
    check("一键清空 -> 200", status == 200, f"status={status}")
    status, body = call("/message/get_page", {"pageNum": 1, "pageSize": 10}, token=student)
    check("清空后 total = 0", (body.get("data") or {}).get("total") == 0, str(body.get("data"))[:200])
    check("清空后未读数为 0", unread(student) == 0, str(unread(student)))

    # ---------- 7. 邮件投递记录 ----------
    print("\n[7] 邮件投递记录")
    status, body = call("/message/get_delivery_log_page", {"pageNum": 1, "pageSize": 10}, token=admin)
    page = body.get("data") or {}
    check("投递记录可分页查询", status == 200 and page.get("total", 0) >= 0, str(page)[:200])
    check("邮箱在记录中已脱敏", all(
        ("***" in (row.get("receiverEmail") or "")) or not row.get("receiverEmail")
        for row in page.get("records") or []), json.dumps(page.get("records") or [], ensure_ascii=False)[:300])

    status, body = call("/message/get_delivery_log_page", {"pageNum": 1, "pageSize": 10, "emailState": "DEAD"}, token=admin)
    dead_records = (body.get("data") or {}).get("records") or []
    check("按 DEAD 筛选可用", status == 200 and all(r.get("emailState") == "DEAD" for r in dead_records), str(dead_records)[:200])

    # ---------- 8. 邮件真实投递 ----------
    print("\n[8] 邮件投递（依赖 SMTP 配置）")
    status, body = call("/message/send_test_email", {"to": "not-an-email"}, token=admin)
    check("测试邮件非法地址 -> 400", status == 400, f"status={status} {body}")

    status, body = call("/message/send_test_email", {"to": "acceptance@lab-reserve.local"}, token=admin)
    smtp_ready = status == 200
    if smtp_ready:
        check("测试邮件发送成功", True)
    else:
        skip("测试邮件发送成功", f"SMTP 未配置：{(body or {}).get('message')}")

    if smtp_ready:
        # 开启学生邮件通知，再发一条，等它变成 OK
        _, profile = call("/user/get_self_profile", {}, token=student)
        old_notify = (profile.get("data") or {}).get("notifyEmail")
        call("/user/update_self", {"email": f"{STUDENT}@lab-reserve.local", "notifyEmail": 1}, token=student)

        status, body = call("/message/send", {
            "receiverIds": [stu_id], "title": "邮件链路验收", "content": "这条消息应当同时到达站内信与邮箱。"
        }, token=admin)
        check("发送带邮件的通知", status == 200, f"status={status}")

        _, page = call("/message/get_page", {"pageNum": 1, "pageSize": 5}, token=student)
        msg_id = ((page.get("data") or {}).get("records") or [{}])[0].get("id")
        ok, row = wait_email_state(admin, msg_id, expect="OK", timeout=45)
        check("邮件在 45s 内投递成功（email_state=OK）", ok, json.dumps(row, ensure_ascii=False))

        status, body = call("/message/get_delivery_log_page", {"pageNum": 1, "pageSize": 10, "emailState": "OK"}, token=admin)
        check("投递记录里能查到 OK 记录", status == 200
              and any(r.get("messageId") == msg_id for r in (body.get("data") or {}).get("records") or []),
              str(body.get("data"))[:200])

        # 还原学生的邮件通知设置，避免影响其他验收
        call("/user/update_self", {"notifyEmail": 0 if old_notify != 1 else 1}, token=student)
        call("/message/clear", {}, token=student)
    else:
        skip("邮件在 45s 内投递成功", "SMTP 未配置")
        skip("投递记录里能查到 OK 记录", "SMTP 未配置")

    # ---------- 收尾 ----------
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
