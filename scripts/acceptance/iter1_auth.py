#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 1（认证与权限）验收脚本。

用法：
  1) ./dev.sh start           # 起本机开发环境（docker 库 + mvnw 后端 + npm 前端）
  2) python3 scripts/acceptance/iter1_auth.py

仅用标准库（urllib），不需要 pip 安装任何依赖。
"""

import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
results = []


def call(path, body=None, token=None, raw=False):
    """发一个 POST 请求，返回 (http_status, body_json_or_bytes)。"""
    url = BASE + path
    data = json.dumps(body or {}).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json;charset=UTF-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read()
            return resp.status, (payload if raw else json.loads(payload.decode("utf-8")))
    except urllib.error.HTTPError as e:
        payload = e.read()
        if raw:
            return e.code, payload
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except Exception:
            return e.code, {"raw": payload[:200].decode("utf-8", "ignore")}


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("  [OK] " if ok else "  [FAIL] ") + name + ("" if ok else f"  -> {detail}"))


def login(username, password):
    return call("/auth/login", {"username": username, "password": password})


def main():
    print("=" * 78)
    print("迭代 1 验收：认证与权限")
    print("=" * 78)

    # ---------- 1. 未登录 ----------
    print("\n[1] 未登录访问受保护接口")
    status, body = call("/operation_log/get_page", {"pageNum": 1})
    check("未带 token 调 /api/operation_log/get_page -> 401",
          status == 401 and body.get("code") == 401, f"status={status} body={body}")
    status, body = call("/auth/get_info")
    check("未带 token 调 /api/auth/get_info -> 401", status == 401, f"status={status}")
    status, body = call("/system/health")
    check("健康检查免登录可访问", status == 200, f"status={status}")

    # ---------- 2. 五角色登录 ----------
    print("\n[2] 五个角色登录 + 菜单差异")
    accounts = [
        ("admin", "admin123", "SYS_ADMIN"),
        ("labadmin", "123456", "LAB_ADMIN"),
        ("repair01", "123456", "MAINTAINER"),
        ("stu2021001", "123456", "STUDENT"),
        ("tea1001", "123456", "TEACHER"),
    ]
    tokens, menus, perms = {}, {}, {}
    for username, password, expect_role in accounts:
        status, body = login(username, password)
        ok = status == 200 and body.get("code") == 200
        check(f"{username} 登录成功（期望角色 {expect_role}）", ok, f"status={status} body={body}")
        if not ok:
            continue
        data = body["data"]
        tokens[username] = data["accessToken"]
        check(f"{username} 角色码 = {expect_role}",
              data["user"]["roleCode"] == expect_role, data["user"]["roleCode"])
        check(f"{username} 手机号已脱敏",
              "****" in (data["user"]["phone"] or ""), data["user"]["phone"])
        perms[username] = set(data["permissions"])
        _, info = call("/auth/get_info", {}, token=data["accessToken"])
        menus[username] = json.dumps(info["data"]["menus"], ensure_ascii=False)
        print(f"      - {username} 权限 {len(data['permissions'])} 项，菜单：{menus[username]}")

    admin_token = tokens.get("admin")
    check("系统管理员菜单含「角色权限」", "角色权限" in menus.get("admin", ""), menus.get("admin", ""))
    check("系统管理员菜单含「操作日志」", "操作日志" in menus.get("admin", ""), menus.get("admin", ""))
    check("实验室管理员菜单不含「角色权限」", "角色权限" not in menus.get("labadmin", ""), menus.get("labadmin", ""))
    check("实验室管理员菜单含「操作日志」", "操作日志" in menus.get("labadmin", ""), menus.get("labadmin", ""))
    check("维修人员菜单只有首页", "系统管理" not in menus.get("repair01", ""), menus.get("repair01", ""))
    check("学生菜单只有首页", "系统管理" not in menus.get("stu2021001", ""), menus.get("stu2021001", ""))
    check("教师菜单只有首页", "系统管理" not in menus.get("tea1001", ""), menus.get("tea1001", ""))
    check("学生有 reservation:equipment:create 且无 reservation:lab:create",
          "reservation:equipment:create" in perms.get("stu2021001", set())
          and "reservation:lab:create" not in perms.get("stu2021001", set()),
          str(sorted(perms.get("stu2021001", set()))))
    check("教师有 reservation:lab:create（实验室排课）",
          "reservation:lab:create" in perms.get("tea1001", set()), str(sorted(perms.get("tea1001", set()))))

    # ---------- 3. 权限拦截 ----------
    print("\n[3] 权限不足 -> 403")
    cases = [
        ("学生调 /role/get_list", "stu2021001", "/role/get_list", {}),
        ("学生调 /operation_log/get_page", "stu2021001", "/operation_log/get_page", {"pageNum": 1}),
        ("实验室管理员调 /role/update_permission", "labadmin", "/role/update_permission",
         {"roleId": 1, "permissionIds": []}),
        ("维修人员调 /operation_log/export", "repair01", "/operation_log/export", {}),
    ]
    for name, user, path, body in cases:
        status, resp = call(path, body, token=tokens.get(user))
        check(f"{name} -> 403", status == 403 and resp.get("code") == 403, f"status={status} body={resp}")

    status, resp = call("/role/get_list", {}, token=admin_token)
    check("系统管理员调 /role/get_list -> 200", status == 200, f"status={status}")
    roles = {r["code"]: r for r in resp.get("data", [])}
    check("角色列表返回 5 个角色 + 权限数/用户数",
          len(roles) == 5 and all("permissionCount" in r and "userCount" in r for r in roles.values()),
          json.dumps(resp.get("data"), ensure_ascii=False)[:200])
    print("      - 角色权限数：" + "、".join(f"{c}={r['permissionCount']}" for c, r in roles.items()))

    status, resp = call("/permission/get_list", {}, token=admin_token)
    groups = resp.get("data", [])
    all_codes = {i["code"] for g in groups for i in g["items"]}
    check("权限点分组列表可用（含 class 模块）",
          status == 200 and any(g["module"] == "class" for g in groups),
          f"组数={len(groups)} 权限数={len(all_codes)}")
    check("权限点已按变更 1 拆分（lab/equipment/timetable/class:manage）",
          {"reservation:lab:create", "reservation:equipment:create", "reservation:timetable",
           "class:list", "class:manage"} <= all_codes
          and "reservation:create" not in all_codes,
          f"共 {len(all_codes)} 个")
    id_by_code = {i["code"]: i["id"] for g in groups for i in g["items"]}

    # ---------- 4. 角色权限保存 ----------
    print("\n[4] 保存角色权限：防锁死 + 缓存失效")
    sys_id = roles["SYS_ADMIN"]["id"]
    lab_id = roles["LAB_ADMIN"]["id"]
    _, resp = call("/role/get_permission_list", {"roleId": sys_id}, token=admin_token)
    sys_perm_ids = resp["data"]["permissionIds"]
    status, resp = call("/role/update_permission",
                        {"roleId": sys_id,
                         "permissionIds": [i for i in sys_perm_ids if i != id_by_code["system:role:update"]]},
                        token=admin_token)
    check("移除 SYS_ADMIN 的 system:role:update -> 400", status == 400, f"status={status} body={resp}")
    check("400 提示语说明原因", "system:role:update" in str(resp.get("message", "")), str(resp.get("message")))

    _, resp = call("/role/get_permission_list", {"roleId": lab_id}, token=admin_token)
    lab_perm_ids = resp["data"]["permissionIds"]
    check("LAB_ADMIN 原本有 stat:view", id_by_code["stat:view"] in lab_perm_ids)
    status, resp = call("/role/update_permission",
                        {"roleId": lab_id,
                         "permissionIds": [i for i in lab_perm_ids if i != id_by_code["stat:view"]]},
                        token=admin_token)
    check("保存成功 -> 200", status == 200, f"status={status} body={resp}")
    _, resp = login("labadmin", "123456")
    check("LAB_ADMIN 重新登录后 stat:view 已消失",
          "stat:view" not in resp["data"]["permissions"], str(resp["data"]["permissions"]))
    call("/role/update_permission", {"roleId": lab_id, "permissionIds": lab_perm_ids}, token=admin_token)
    _, resp = login("labadmin", "123456")
    check("还原后 stat:view 回来", "stat:view" in resp["data"]["permissions"])

    # ---------- 5. 操作日志 ----------
    print("\n[5] 操作日志")
    status, resp = call("/operation_log/get_page", {"pageNum": 1, "pageSize": 50, "module": "auth"},
                        token=admin_token)
    records = resp.get("data", {}).get("records", [])
    check("能查到 auth 模块日志", status == 200 and len(records) > 0,
          f"status={status} total={resp.get('data', {}).get('total')}")
    login_logs = [r for r in records if r["operationType"] == "LOGIN"]
    check("有成功登录日志（result=1）", any(r["result"] == 1 for r in login_logs), str(len(login_logs)))
    check("登录日志记录了真实 IP", all(r["ip"] for r in login_logs), str([r["ip"] for r in login_logs][:3]))
    check("登录日志补齐了操作人姓名与角色（join tb_user/tb_role）",
          any(r["operatorName"] and r["roleCode"] for r in login_logs),
          json.dumps(login_logs[:1], ensure_ascii=False)[:200])
    check("登录日志不记录密码参数（saveParam=false）",
          all("password" not in (r.get("requestParam") or "").lower() for r in login_logs))
    status, resp = call("/operation_log/get_page", {"pageNum": 1, "pageSize": 10, "result": 0},
                        token=admin_token)
    check("支持按结果失败筛选", status == 200, f"status={status}")
    status, resp = call("/operation_log/get_page",
                        {"pageNum": 1, "pageSize": 10, "operatorKeyword": "系统管理员"}, token=admin_token)
    check("支持按操作人姓名筛选", status == 200 and resp["data"]["total"] > 0,
          f"total={resp.get('data', {}).get('total')}")
    status, resp = call("/operation_log/get_page",
                        {"pageNum": 1, "pageSize": 10, "operatorKeyword": "不存在的人"}, token=admin_token)
    check("操作人筛选无结果时返回空而不是报错",
          status == 200 and resp["data"]["total"] == 0, f"status={status}")
    if records:
        status, resp = call("/operation_log/get_info", {"id": records[0]["id"]}, token=admin_token)
        check("日志详情可查", status == 200 and resp["data"]["id"] == records[0]["id"], f"status={status}")
    status, blob = call("/operation_log/export", {"operationType": "LOGIN"}, token=admin_token, raw=True)
    check("导出 Excel（xlsx）成功",
          status == 200 and blob[:2] == b"PK" and len(blob) > 1000,
          f"status={status} size={len(blob) if isinstance(blob, bytes) else '?'}")

    # ---------- 6. 登录锁定 ----------
    print("\n[6] 登录失败 5 次锁定 15 分钟（测试账号 stu2021002）")
    for i in range(1, 6):
        status, resp = login("stu2021002", "wrong-password")
        print(f"      第 {i} 次失败：HTTP {status} · {resp.get('message', '')}")
    status, resp = login("stu2021002", "123456")
    check("锁定后即使密码正确也被拒（400 + 锁定提示）",
          status == 400 and "锁定" in resp.get("message", ""), f"status={status} {resp.get('message')}")

    # ---------- 7. token 生命周期 ----------
    print("\n[7] token 生命周期")
    _, body = login("stu2021003", "123456")
    token3, refresh3 = body["data"]["accessToken"], body["data"]["refreshToken"]
    status, resp = call("/auth/refresh", {"refreshToken": refresh3})
    check("refresh 能换到新 accessToken", status == 200 and resp["data"]["accessToken"], f"status={status}")
    status, resp = call("/auth/update_password",
                        {"oldPassword": "wrong", "newPassword": "Abc@123456"}, token=token3)
    check("原密码错误时改密失败（400）", status == 400, f"status={status} {resp.get('message')}")
    status, resp = call("/auth/update_password",
                        {"oldPassword": "123456", "newPassword": "Abc@123456"}, token=token3)
    check("改密成功 -> 200", status == 200, f"status={status} body={resp}")
    status, resp = call("/auth/get_info", {}, token=token3)
    check("改密后旧 accessToken 立即失效（401）", status == 401, f"status={status}")
    status, resp = call("/auth/refresh", {"refreshToken": refresh3})
    check("改密后旧 refreshToken 也失效（401）", status == 401, f"status={status}")
    status, resp = login("stu2021003", "Abc@123456")
    check("可用新密码登录", status == 200, f"status={status}")
    new_token = resp["data"]["accessToken"]
    status, resp = call("/auth/logout", {}, token=new_token)
    check("登出 -> 200", status == 200, f"status={status}")
    status, resp = call("/auth/get_info", {}, token=new_token)
    check("登出后该 token 已进黑名单（401）", status == 401, f"status={status}")
    _, body = login("stu2021003", "Abc@123456")
    status, resp = call("/auth/update_password",
                        {"oldPassword": "Abc@123456", "newPassword": "123456"},
                        token=body["data"]["accessToken"])
    check("密码已还原为 123456", status == 200, f"status={status}")

    # ---------- 汇总 ----------
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
