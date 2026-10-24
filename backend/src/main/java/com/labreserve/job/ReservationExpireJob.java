package com.labreserve.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.TimeSlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预约过期扫描：{@code APPROVED → EXPIRED}。
 *
 * <p>判据：预约的**结束时刻**（{@code reserve_date + slot.end_time}）已经过去。
 * 扫描范围只到「今天」，历史数据不会每次全表扫。
 *
 * <p>为什么必须有它：否则教师约了实验室却没去上课，预约会一直停在 {@code APPROVED}，
 * 统计的成功率、实验室占用率、管理端列表都会把它当成"正在生效"，与事实不符。
 *
 * <p>间隔可用 {@code RESERVATION_EXPIRE_INTERVAL_MS} 覆盖（默认 1 小时），
 * 验收时调小即可快速看到过期效果。
 */
@Slf4j
@Component
public class ReservationExpireJob {

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Scheduled(fixedDelayString = "${lab-reserve.reservation.expire-interval-ms:3600000}",
            initialDelayString = "${lab-reserve.reservation.expire-initial-delay-ms:60000}")
    public void expire() {
        LocalDate today = LocalDate.now();
        List<Reservation> candidates = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, "APPROVED")
                .le(Reservation::getReserveDate, today));
        if (candidates.isEmpty()) {
            return;
        }
        Map<Long, TimeSlot> slots = new LinkedHashMap<>();
        List<Long> slotIds = candidates.stream().map(Reservation::getSlotId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!slotIds.isEmpty()) {
            timeSlotMapper.selectBatchIds(slotIds).forEach(item -> slots.put(item.getId(), item));
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> expiredIds = candidates.stream()
                .filter(item -> endedBefore(item, slots.get(item.getSlotId()), now))
                .map(Reservation::getId)
                .toList();
        if (expiredIds.isEmpty()) {
            return;
        }
        int updated = reservationMapper.update(null, Wrappers.<Reservation>lambdaUpdate()
                .in(Reservation::getId, expiredIds)
                .eq(Reservation::getStatus, "APPROVED")
                .set(Reservation::getStatus, "EXPIRED"));
        if (updated > 0) {
            log.info("[预约] 过期扫描：{} 条 APPROVED 预约转为 EXPIRED（候选 {} 条）", updated, candidates.size());
        }
    }

    /** 结束时刻 = 日期 + 时段结束时间；时段缺失时按「日期已过」判定 */
    private boolean endedBefore(Reservation reservation, TimeSlot slot, LocalDateTime now) {
        if (reservation.getReserveDate() == null) {
            return false;
        }
        if (slot == null || slot.getEndTime() == null) {
            return reservation.getReserveDate().isBefore(now.toLocalDate());
        }
        return LocalDateTime.of(reservation.getReserveDate(), slot.getEndTime()).isBefore(now);
    }
}
