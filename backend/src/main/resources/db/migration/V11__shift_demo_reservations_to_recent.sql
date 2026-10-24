-- ============================================================================
-- V7：把演示预约的日期平移到「今天附近」
--
-- 背景：种子数据早期用固定日期 2025-09 / 2025-10 生成 360 条演示预约，
-- 到 2026 年演示时全部"已过期"，导致「待审核列表」「我的实验课表」「统计报表」
-- 打开都是空的，无法演示迭代 5 的审核 / 课表链路。
--
-- 做法：按资源类型整体平移（保持相对关系不变，因此 occupy_key 唯一性不受影响）：
--   实验室预约：2025-09-01 → CURDATE() - 2 天（覆盖 今天-2 ~ 今天+1，每天 6 条 PENDING）
--   设备预约  ：2025-10-01 → CURDATE() - 1 天（覆盖 今天-1 ~ 今天+1）
--   排队演示  ：2025-10-10/11 → 明天/后天
--
-- 说明：这是**演示数据修正**，不是业务结构变更；生产环境可跳过本脚本。
-- 幂等性：重复执行会因为 DATEDIFF 归零而不再移动（第二次执行 delta=0）。
-- ============================================================================

-- 1) 实验室预约：reserve_date / created_at / audit_time 一起平移，保持时间线合理
UPDATE tb_reservation
SET reserve_date = DATE_ADD(reserve_date,
                            INTERVAL DATEDIFF(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '2025-09-01') DAY),
    created_at   = DATE_ADD(created_at,
                            INTERVAL DATEDIFF(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '2025-09-01') DAY),
    audit_time   = IF(audit_time IS NULL, NULL,
                      DATE_ADD(audit_time,
                               INTERVAL DATEDIFF(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '2025-09-01') DAY))
WHERE resource_type = 1
  AND reserve_date BETWEEN '2025-09-01' AND '2025-09-30';

-- 2) 设备预约
UPDATE tb_reservation
SET reserve_date = DATE_ADD(reserve_date,
                            INTERVAL DATEDIFF(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '2025-10-01') DAY),
    created_at   = DATE_ADD(created_at,
                            INTERVAL DATEDIFF(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '2025-10-01') DAY),
    audit_time   = IF(audit_time IS NULL, NULL,
                      DATE_ADD(audit_time,
                               INTERVAL DATEDIFF(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '2025-10-01') DAY))
WHERE resource_type = 2
  AND reserve_date BETWEEN '2025-10-01' AND '2025-10-31';

-- 3) 排队演示记录（迭代 6 用）：2025-10-10/11 → 明天/后天
UPDATE tb_resource_queue
SET reserve_date = DATE_ADD(reserve_date,
                            INTERVAL DATEDIFF(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '2025-10-10') DAY)
WHERE reserve_date IN ('2025-10-10', '2025-10-11');
