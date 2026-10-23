#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 2（用户管理）验收脚本。

用法：
  1) ./dev.sh start
  2) python3 scripts/acceptance/iter2_user.py

脚本可重复执行：创建的用户/班级若已存在会复用（不会因为第二次跑就失败）。
仅用标准库（urllib）。
"""

import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
results = []

# 测试数据（固定值，便于重复执行时定位）
STUDENT_USERNAME = "t2025001"
STUDENT_NO = "T2025001"
TEACHER_USERNAME = "tt2001"
TEACHER_NO = "TT2001"
MAINTAINER_USERNAME = "tm2001"
MAINTAINER_NO = "TM2001"
CLASS_NAME = "测试2501"


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


def login(username, password="123456"):
    status, body = call("/auth/login", {"username": username, "password": password})
    return status, body, (body.get("data") or {}).get("accessToken")


def find_user_id(token, keyword):
    _, body = call("/user/get_page", {"keyword": keyword, "pageSize": 10}, token=token)
    for row in (body.get("data") or {}).get("records", []):
        if row["username"] == keyword:
            return row["id"], row
    return None, None


def main():
    print("=" * 78)
    print("迭代 2 验收：用户管理")
    print("=" * 78)

    # ---------- 登录 ----------
    print("\n[0] 准备：登录 admin / labadmin / student")
    status, body, admin = login("admin", "123456")
    check("admin 登录", status == 200 and admin, f"status={status} {body}")
    _, _, labadmin = login("labadmin")
    _, _, student = login("stu2021001")

    # ---------- 1. 新增学生 ----------
    print("\n[1] 系统管理员新增学生")
    status, body = call("/user/create", {
        "username": STUDENT_USERNAME, "password": "123456", "realName": "测试学生",
        "roleCode": "STUDENT", "phone": "13800009999", "email": "t2025001@x.com",
        # 不传 notifyEmail：验证「邮件通知默认关闭」
        "studentInfo": {"studentNo": STUDENT_NO, "classId": 1},
    }, token=admin)
    if status == 200:
        student_id = body["data"]
        check(f"新增学生成功（id={student_id}）", True)
    else:
        student_id, _ = find_user_id(admin, STUDENT_USERNAME)
        check("新增学生成功或已存在（可重复执行）", student_id is not None, f"status={status} body={body}")
    check("新学生可以用初始密码登录", login(STUDENT_USERNAME)[0] == 200)
    _, detail = call("/user/get_info", {"id": student_id}, token=admin)
    check("新建用户「邮件通知」默认关闭（notifyEmail=0）",
          (detail.get("data") or {}).get("notifyEmail") == 0,
          str((detail.get("data") or {}).get("notifyEmail")))

    # ---------- 2. 学号唯一 ----------
    print("\n[2] 学号唯一性（不能 500）")
    status, body = call("/user/create", {
        "username": STUDENT_USERNAME + "dup", "password": "123456", "realName": "重复学号",
        "roleCode": "STUDENT", "studentInfo": {"studentNo": STUDENT_NO, "classId": 1},
    }, token=admin)
    check("重复学号 -> 400 且提示「学号已存在」",
          status == 400 and "学号已存在" in str(body.get("message")), f"status={status} body={body}")
    status, body = call("/user/create", {
        "username": STUDENT_USERNAME, "password": "123456", "realName": "重复账号",
        "roleCode": "STUDENT", "studentInfo": {"studentNo": "T999999", "classId": 1},
    }, token=admin)
    check("重复账号 -> 400 且提示「账号已存在」",
          status == 400 and "账号已存在" in str(body.get("message")), f"status={status} body={body}")

    # ---------- 3. 新增教师 / 维修人员 ----------
    print("\n[3] 新增教师 / 维修人员（角色决定表单字段）")
    status, body = call("/user/create", {
        "username": TEACHER_USERNAME, "password": "123456", "realName": "测试教师",
        "roleCode": "TEACHER", "phone": "13700009999",
        "teacherInfo": {"teacherNo": TEACHER_NO, "college": "计算机学院", "title": "副教授"},
    }, token=admin)
    teacher_id = body.get("data") if status == 200 else find_user_id(admin, TEACHER_USERNAME)[0]
    check("新增教师成功", teacher_id is not None, f"status={status} body={body}")
    _, detail = call("/user/get_info", {"id": teacher_id}, token=admin)
    info = detail.get("data") or {}
    check("教师详情有 teacherInfo（工号/学院/职称）",
          (info.get("teacherInfo") or {}).get("teacherNo") == TEACHER_NO
          and (info.get("teacherInfo") or {}).get("title") == "副教授",
          json.dumps(info.get("teacherInfo"), ensure_ascii=False))

    status, body = call("/user/create", {
        "username": MAINTAINER_USERNAME, "password": "123456", "realName": "测试维修",
        "roleCode": "MAINTAINER", "phone": "13600009999",
        "staffInfo": {"staffNo": MAINTAINER_NO, "department": "校外维保单位", "maintainerType": 2},
    }, token=admin)
    maintainer_id = body.get("data") if status == 200 else find_user_id(admin, MAINTAINER_USERNAME)[0]
    check("新增维修人员成功", maintainer_id is not None, f"status={status} body={body}")
    _, detail = call("/user/get_info", {"id": maintainer_id}, token=admin)
    staff = (detail.get("data") or {}).get("staffInfo") or {}
    check("维修人员详情有 staffInfo（工号/部门/校内校外）",
          staff.get("staffNo") == MAINTAINER_NO and staff.get("maintainerTypeName") == "校外",
          json.dumps(staff, ensure_ascii=False))

    # ---------- 4. 列表与脱敏 ----------
    print("\n[4] 用户列表：手机号默认脱敏")
    status, body = call("/user/get_page", {"keyword": "测试", "pageSize": 20}, token=admin)
    rows = (body.get("data") or {}).get("records", [])
    check("能查到刚创建的用户", status == 200 and len(rows) >= 2, f"status={status} total={body.get('data', {}).get('total')}")
    check("列表里手机号是脱敏的（138****9999）",
          all("****" in (r.get("phone") or "") for r in rows if r.get("phone")),
          str([r.get("phone") for r in rows][:3]))
    check("列表带出学生学号 / 班级 / 学院（JOIN）",
          any(r.get("studentNo") == STUDENT_NO and r.get("className") for r in rows),
          json.dumps(rows[:1], ensure_ascii=False)[:200])

    # ---------- 5. revealPhone + QUERY_PHONE 日志 ----------
    print("\n[5] revealPhone=true -> 明文 + QUERY_PHONE 日志")
    _, body = call("/user/get_info", {"id": student_id, "revealPhone": False}, token=admin)
    check("revealPhone=false 时脱敏", "****" in ((body.get("data") or {}).get("phone") or ""),
          str((body.get("data") or {}).get("phone")))
    _, body = call("/user/get_info", {"id": student_id, "revealPhone": True}, token=admin)
    plain = (body.get("data") or {}).get("phone")
    # 不写死号码：脚本会改自己的手机号，重复执行时值会变
    check("revealPhone=true 且有权限 -> 明文（11 位、无掩码）",
          bool(plain) and "****" not in plain and len(plain) == 11, str(plain))

    status, body = call("/operation_log/get_page",
                        {"pageNum": 1, "pageSize": 10, "operationType": "QUERY_PHONE"}, token=admin)
    logs = (body.get("data") or {}).get("records", [])
    check("操作日志里出现 QUERY_PHONE 记录", status == 200 and len(logs) > 0,
          f"status={status} total={body.get('data', {}).get('total')}")
    check("QUERY_PHONE 日志带操作人与真实 IP",
          any(x.get("operatorName") and x.get("ip") for x in logs),
          json.dumps(logs[:1], ensure_ascii=False)[:200])

    # ---------- 6. 无 user:create 的角色 ----------
    print("\n[6] 实验室管理员不能新增用户（需求 Q32）")
    status, body = call("/user/create", {
        "username": "should_fail_01", "password": "123456", "realName": "不应成功",
        "roleCode": "STUDENT", "studentInfo": {"studentNo": "T0000001", "classId": 1},
    }, token=labadmin)
    check("LAB_ADMIN 调 /user/create -> 403", status == 403, f"status={status} body={body}")
    status, body = call("/user/update_status", {"id": student_id, "status": 0}, token=labadmin)
    check("LAB_ADMIN 调 /user/update_status -> 403", status == 403, f"status={status} body={body}")

    # ---------- 7. 学生侧 ----------
    print("\n[7] 学生：无用户管理菜单、改不了别人")
    _, _, student2 = login(STUDENT_USERNAME)
    status, body = call("/auth/get_info", {}, token=student2)
    menus = json.dumps((body.get("data") or {}).get("menus"), ensure_ascii=False)
    check("学生菜单里没有「用户管理」", "用户管理" not in menus and "角色权限" not in menus, menus)
    status, body = call("/user/get_page", {"pageNum": 1}, token=student2)
    check("学生调 /user/get_page -> 403", status == 403, f"status={status}")
    status, body = call("/user/update", {"id": 1, "realName": "改名"}, token=student2)
    check("学生调 /user/update（改别人）-> 403", status == 403, f"status={status}")

    status, body = call("/user/update_self", {"phone": "13911112222", "notifyEmail": 1}, token=student2)
    check("学生修改自己的手机号 -> 200", status == 200, f"status={status} body={body}")
    status, body = call("/user/get_self_profile", {}, token=student2)
    me = body.get("data") or {}
    check("我的档案里手机号是明文（本人可见）", me.get("phone") == "13911112222", str(me.get("phone")))
    check("我的档案带学生扩展信息（班级/学院）",
          (me.get("studentInfo") or {}).get("className") == "软工2101",
          json.dumps(me.get("studentInfo"), ensure_ascii=False))

    # 还原邮件通知开关：默认应为关闭，避免脚本把演示账号留成「已开启」
    call("/user/update_self", {"notifyEmail": 0}, token=student2)

    # ---------- 8. 注销（不物理删除） ----------
    print("\n[8] 注销用户：不物理删除 + 登录被拒")
    status, body = call("/user/update_status",
                        {"id": student_id, "status": 0, "reason": "验收脚本测试"}, token=admin)
    check("注销 -> 200", status == 200, f"status={status} body={body}")
    status, body = call("/user/get_info", {"id": student_id}, token=admin)
    check("注销后用户仍能查到（status=0，未物理删除）",
          status == 200 and (body.get("data") or {}).get("status") == 0, f"status={status} body={body}")
    check("注销后姓名仍完整（历史数据不会断链）",
          (body.get("data") or {}).get("realName") == "测试学生", str((body.get("data") or {}).get("realName")))
    status, body = call("/auth/login", {"username": STUDENT_USERNAME, "password": "123456"})
    check("已注销账号登录 -> 403「账号已注销」",
          status == 403 and "注销" in str(body.get("message")), f"status={status} {body.get('message')}")
    status, body = call("/user/update_status", {"id": 1, "status": 0}, token=admin)
    check("不能注销自己 -> 400", status == 400, f"status={status} body={body}")
    status, body = call("/user/update_status", {"id": student_id, "status": 1}, token=admin)
    check("恢复用户 -> 200", status == 200, f"status={status} body={body}")

    # ---------- 9. 班级 ----------
    print("\n[9] 班级维护")
    status, body = call("/class/create", {
        "className": CLASS_NAME, "college": "计算机学院", "major": "软件工程",
        "grade": 2025, "studentCount": 30,
    }, token=admin)
    if status == 200:
        class_id = body["data"]
        check(f"新增班级成功（id={class_id}）", True)
    else:
        class_id = None
        status2, body2 = call("/class/get_page", {"keyword": CLASS_NAME}, token=admin)
        for row in (body2.get("data") or {}).get("records", []):
            if row["className"] == CLASS_NAME:
                class_id = row["id"]
        check("新增班级成功或已存在（可重复执行）", class_id is not None, f"status={status} body={body}")

    status, body = call("/class/create", {
        "className": CLASS_NAME, "college": "计算机学院", "major": "软件工程", "grade": 2025,
    }, token=admin)
    check("重复班级名 -> 400 且提示", status == 400 and "已存在" in str(body.get("message")),
          f"status={status} body={body}")

    status, body = call("/class/get_list", {}, token=student2)
    names = [c["className"] for c in (body.get("data") or [])]
    check("班级下拉可用（含新建班级）", status == 200 and CLASS_NAME in names, f"status={status} {names[:5]}")
    status, body = call("/class/get_page", {"pageNum": 1, "pageSize": 50}, token=labadmin)
    # 需求变更 2：LAB_ADMIN 不再具备 class:manage / user:list / system:log:list
    check("LAB_ADMIN 调班级分页 -> 403（无 class:manage）", status == 403, f"status={status}")
    status, body = call("/user/get_page", {"pageNum": 1}, token=labadmin)
    check("LAB_ADMIN 调用户分页 -> 403（无 user:list）", status == 403, f"status={status}")
    status, body = call("/operation_log/get_page", {"pageNum": 1}, token=labadmin)
    check("LAB_ADMIN 调操作日志 -> 403（无 system:log:list）", status == 403, f"status={status}")
    status, body = call("/class/get_page", {"pageNum": 1}, token=student2)
    check("学生调班级分页 -> 403", status == 403, f"status={status}")

    # ---------- 10. 下拉 / 派单 ----------
    print("\n[10] 下拉与派单")
    status, body = call("/user/list_maintainer", {}, token=labadmin)
    maintainers = body.get("data") or []
    check("维修人员下拉可用且带校内/校外标签",
          status == 200 and any(m.get("staffNo") == MAINTAINER_NO and m.get("maintainerTypeName") == "校外"
                                for m in maintainers),
          json.dumps(maintainers[:2], ensure_ascii=False))
    status, body = call("/user/list_maintainer", {}, token=student2)
    check("学生调维修人员下拉 -> 403", status == 403, f"status={status}")
    status, body = call("/user/list_option", {"roleCode": "TEACHER"}, token=student2)
    check("用户下拉可用（学生也能调，按角色过滤）",
          status == 200 and any(u.get("realName") == "测试教师" for u in (body.get("data") or [])),
          json.dumps(body.get("data"), ensure_ascii=False)[:200])

    # ---------- 11. 越权修改已被拒绝（数据未变） ----------
    print("\n[11] 数据完整性")
    _, body = call("/user/get_info", {"id": 1}, token=admin)
    check("系统管理员姓名未被学生的越权请求改掉",
          (body.get("data") or {}).get("realName") == "系统管理员",
          str((body.get("data") or {}).get("realName")))

    # ---------- 12. 系统运行状态只给系统管理员 ----------
    print("\n[12] 系统运行状态权限")
    status, body = call("/system/health", {})
    health = body.get("data") or {}
    check("公开健康检查只返回 status，不含 DB/Redis 明细",
          status == 200 and "database" not in health and "redis" not in health,
          json.dumps(health, ensure_ascii=False))
    status, body = call("/system/monitor", {}, token=admin)
    monitor = body.get("data") or {}
    check("系统管理员可查看运行状态（含 database / redis / 运行时长）",
          status == 200 and monitor.get("database") and monitor.get("redis")
          and monitor.get("uptimeSeconds") is not None,
          json.dumps(monitor, ensure_ascii=False))

    labadmin_monitor, student_monitor, maintainer_monitor = (
        call("/system/monitor", {}, token=labadmin)[0],
        call("/system/monitor", {}, token=student2)[0],
        call("/system/monitor", {}, token=login("repair01")[2])[0],
    )
    check("LAB_ADMIN 调 /system/monitor -> 403", labadmin_monitor == 403, f"status={labadmin_monitor}")
    check("STUDENT 调 /system/monitor -> 403", student_monitor == 403, f"status={student_monitor}")
    check("MAINTAINER 调 /system/monitor -> 403", maintainer_monitor == 403, f"status={maintainer_monitor}")

    status, body = call("/auth/get_info", {}, token=admin)
    admin_perms = (body.get("data") or {}).get("permissions") or []
    check("只有系统管理员拥有 system:monitor:view",
          "system:monitor:view" in admin_perms,
          json.dumps(admin_perms, ensure_ascii=False)[:200])

    print("\n" + "=" * 78)
    passed = sum(1 for _, ok, _ in results if ok)
    failed = [(n, d) for n, ok, d in results if not ok]
    print(f"共 {len(results)} 项：通过 {passed}，失败 {len(failed)}")
    for name, detail in failed:
        print(f"  [FAIL] {name} -> {detail}")
    print("=" * 78)
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
