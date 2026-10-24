#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""迭代 8（统计报表）验收脚本。

覆盖：权限、4 个核心指标与手工 SQL 对账、学院/实验类型筛选、
趋势分组、占用率缓存（清 Redis 后回源仍正确）、5 种 Excel 导出、导出留痕。

用法：
    python3 scripts/acceptance/iter8_statistics.py

脚本会通过 docker exec 直接跑 SQL 做对账；没有 docker 时跳过对账类断言。
"""

import json
import subprocess
import urllib.error
import urllib.request
from datetime import date, timedelta

BASE = "http://localhost:8080/api"
results = []
skipped = []


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


def post_blob(path, body, token):
    req = urllib.request.Request(BASE + path, data=json.dumps(body or {}).encode(), method="POST")
    req.add_header("Content-Type", "application/json;charset=UTF-8")
    req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.status, resp.headers.get("Content-Type"), resp.read()


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
    return titles


def mysql(sql):
    cmd = ["docker", "exec", "lab-reserve-mysql", "sh", "-c",
           f'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "use lab_reserve; {sql}"']
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if proc.returncode != 0:
            return None
        return proc.stdout.strip()
    except Exception:
        return None


def redis_cli(*args):
    cmd = ["docker", "exec", "lab-reserve-redis", "sh", "-c",
           'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning "$@"', "sh", *args]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
        return proc.stdout.strip() if proc.returncode == 0 else None
    except Exception:
        return None


def main():
    print("=" * 78)
    print("迭代 8 验收：统计报表（指标对账 / 筛选 / 缓存 / Excel 导出）")
    print("=" * 78)

    admin = login("labadmin")
    sys_admin = login("admin")
    student = login("stu2021001")
    teacher = login("tea1001")
    check("管理员 / 系统管理员 / 学生 / 教师登录", all([admin, sys_admin, student, teacher]))

    today = date.today()
    begin = today.replace(day=1).isoformat()
    end = today.isoformat()
    query = {"beginDate": begin, "endDate": end}

    # ---------- 1. 权限与菜单 ----------
    print("\n[1] 权限与菜单")
    check("学生调统计 -> 403",
          call("/statistics/get_overview", {}, token=student)[0] == 403)
    check("教师调统计 -> 403",
          call("/statistics/get_overview", {}, token=teacher)[0] == 403)
    admin_titles = menu_titles(admin)
    check("实验室管理员菜单含 数据统计", any("数据统计" in t for t in admin_titles), str(admin_titles))
    check("学生菜单不含 数据统计", not any("数据统计" in t for t in menu_titles(student)), str(menu_titles(student)))

    # ---------- 2. 总览与手工 SQL 对账 ----------
    print("\n[2] 总览指标对账")
    status, body = call("/statistics/get_overview", {}, token=admin)
    overview = body.get("data") or {}
    check("总览接口可用", status == 200 and overview.get("reservationTotal") is not None, str(overview)[:200])

    sql_total = mysql("SELECT COUNT(*) FROM tb_reservation;")
    sql_pending = mysql("SELECT COUNT(*) FROM tb_reservation WHERE status='PENDING';")
    sql_today = mysql(f"SELECT COUNT(*) FROM tb_reservation WHERE reserve_date='{today}';")
    if sql_total is None:
        skip("预约总数与 SQL 一致", "docker/mysql 不可用")
        skip("待审核数与 SQL 一致", "docker/mysql 不可用")
        skip("今日预约数与 SQL 一致", "docker/mysql 不可用")
    else:
        check("预约总数与 SQL 一致", int(sql_total) == overview.get("reservationTotal"),
              f"sql={sql_total} api={overview.get('reservationTotal')}")
        check("待审核数与 SQL 一致", int(sql_pending) == overview.get("pendingAudit"),
              f"sql={sql_pending} api={overview.get('pendingAudit')}")
        check("今日预约数与 SQL 一致", int(sql_today) == overview.get("todayReservation"),
              f"sql={sql_today} api={overview.get('todayReservation')}")

    # ---------- 3. 成功率对账 ----------
    print("\n[3] 预约成功率对账（本月）")
    status, body = call("/statistics/get_success_rate", query, token=admin)
    rate = body.get("data") or {}
    check("成功率接口可用", status == 200 and rate.get("total") is not None, str(rate)[:200])
    sql_total_range = mysql(
        f"SELECT COUNT(*) FROM tb_reservation WHERE reserve_date BETWEEN '{begin}' AND '{end}';")
    sql_approved = mysql(
        f"SELECT COUNT(*) FROM tb_reservation WHERE reserve_date BETWEEN '{begin}' AND '{end}' AND status='APPROVED';")
    if sql_total_range is None:
        skip("区间总数与 SQL 一致", "docker/mysql 不可用")
        skip("已通过数与 SQL 一致", "docker/mysql 不可用")
    else:
        check("区间总数与 SQL 一致", int(sql_total_range) == rate.get("total"),
              f"sql={sql_total_range} api={rate.get('total')}")
        check("已通过数与 SQL 一致", int(sql_approved) == rate.get("approved"),
              f"sql={sql_approved} api={rate.get('approved')}")
    check("成功率 = approved / total",
          rate.get("total") in (0, None) or abs(rate.get("successRate", 0)
                                                - rate.get("approved") / rate.get("total")) < 0.001,
          f"{rate.get('approved')}/{rate.get('total')}={rate.get('successRate')}")

    # ---------- 4. 实验类型占比 / 资源占比 ----------
    print("\n[4] 实验类型占比与资源占比")
    status, body = call("/statistics/get_experiment_type_ratio", query, token=admin)
    ratio = body.get("data") or {}
    check("实验类型占比接口可用", status == 200 and len(ratio.get("items") or []) > 0, str(ratio)[:200])
    check("各类型 count 之和 = total",
          sum(item["count"] for item in ratio.get("items") or []) == ratio.get("total"),
          f"{sum(item['count'] for item in ratio.get('items') or [])} vs {ratio.get('total')}")
    check("占比之和 ≈ 1（或为空）",
          abs(sum(item["ratio"] for item in ratio.get("items") or []) - 1) < 0.02 or not ratio.get("items"),
          str([item["ratio"] for item in ratio.get("items") or []]))

    status, body = call("/statistics/get_resource_ratio", query, token=admin)
    resource_ratio = body.get("data") or {}
    check("资源占比接口可用", status == 200 and len(resource_ratio.get("items") or []) > 0,
          str(resource_ratio)[:160])
    check("资源占比条目不超过 15（Top N）", len(resource_ratio.get("items") or []) <= 15,
          str(len(resource_ratio.get("items") or [])))

    # ---------- 5. 趋势 ----------
    print("\n[5] 预约趋势")
    for group_by in ("day", "week", "month"):
        status, body = call("/statistics/get_reservation_trend", {**query, "groupBy": group_by}, token=admin)
        trend = body.get("data") or {}
        items = trend.get("items") or []
        total = sum(item["total"] for item in items)
        check(f"趋势 groupBy={group_by} 可用且 total 求和一致",
              status == 200 and trend.get("groupBy") == group_by
              and (rate.get("total") in (0, None) or total == rate.get("total")),
              f"items={len(items)} sum={total} rateTotal={rate.get('total')}")

    # ---------- 6. 筛选 ----------
    print("\n[6] 筛选条件")
    status, body = call("/statistics/get_success_rate", {**query, "college": "计算机学院"}, token=admin)
    college_rate = body.get("data") or {}
    check("按学院筛选后总数 ≤ 全量", (college_rate.get("total") or 0) <= (rate.get("total") or 0),
          f"{college_rate.get('total')} vs {rate.get('total')}")

    _, body = call("/experiment_type/get_list", {}, token=admin)
    types = body.get("data") or []
    if types:
        type_id = types[0]["id"]
        status, body = call("/statistics/get_success_rate", {**query, "experimentTypeId": type_id}, token=admin)
        filtered = body.get("data") or {}
        check("按实验类型筛选后总数 ≤ 全量", (filtered.get("total") or 0) <= (rate.get("total") or 0),
              f"{filtered.get('total')} vs {rate.get('total')}")

    status, body = call("/statistics/get_success_rate",
                        {"beginDate": "2020-01-01", "endDate": "2020-01-31"}, token=admin)
    empty = body.get("data") or {}
    check("空区间返回全 0", status == 200 and empty.get("total") == 0 and empty.get("successRate") == 0,
          str(empty))

    # ---------- 7. 占用率与缓存 ----------
    print("\n[7] 占用率与缓存")
    status, body = call("/statistics/get_occupancy", query, token=admin)
    occupancy = body.get("data") or {}
    check("占用率接口可用", status == 200 and len(occupancy.get("byLab") or []) > 0, str(occupancy)[:160])
    check("占用率在 0~1 之间",
          all(0 <= (item["occupancyRate"] or 0) <= 1 for item in occupancy.get("byLab") or []),
          str([item["occupancyRate"] for item in (occupancy.get("byLab") or [])[:5]]))
    cache_key = f"lab:reserve:stat:occupancy:{begin}:{end}"
    cached = redis_cli("GET", cache_key)
    if cached is None:
        skip("占用率写入 Redis 缓存", "docker/redis 不可用")
        skip("清缓存后结果不变（回源 DB）", "docker/redis 不可用")
    else:
        check("占用率写入 Redis 缓存", cached is not None and len(cached) > 10, str(len(cached or "")))
        redis_cli("DEL", cache_key)
        _, body2 = call("/statistics/get_occupancy", query, token=admin)
        second = body2.get("data") or {}
        check("清缓存后结果不变（回源 DB）", second.get("overall") == occupancy.get("overall"),
              f"{second.get('overall')} vs {occupancy.get('overall')}")

    # ---------- 8. Excel 导出 ----------
    print("\n[8] Excel 导出")
    for type_name, label in [("reservation", "预约明细"), ("occupancy", "资源占用率"),
                             ("experiment-type", "实验类型占比"), ("success-rate", "预约成功率"),
                             ("resource-ratio", "资源预约占比")]:
        status, content_type, payload = post_blob("/statistics/export", {**query, "type": type_name}, admin)
        is_xlsx = payload[:2] == b"PK"  # xlsx 本质是 zip
        check(f"导出「{label}」成功且是合法 xlsx",
              status == 200 and "spreadsheetml" in (content_type or "") and is_xlsx and len(payload) > 1000,
              f"status={status} type={content_type} size={len(payload)} magic={payload[:2]!r}")

    status, _ = call("/statistics/export", {**query, "type": "reservation"}, token=student)
    check("学生导出统计 -> 403", status == 403, f"status={status}")

    if mysql("SELECT COUNT(*) FROM tb_operation_log WHERE operation_type='EXPORT';") is None:
        skip("导出写入操作日志", "docker/mysql 不可用")
    else:
        count = mysql("SELECT COUNT(*) FROM tb_operation_log WHERE operation_type='EXPORT';")
        check("导出写入操作日志（EXPORT）", int(count) >= 5, f"export_logs={count}")

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
