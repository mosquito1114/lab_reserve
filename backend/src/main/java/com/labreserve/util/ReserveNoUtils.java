package com.labreserve.util;

import com.labreserve.common.constant.RedisKeyConst;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 预约号生成组件：{@code R-YYYYMMDD-XXXX}。
 *
 * <p>按天自增（Redis {@code INCR}），key 48h 过期；这样既不会跨天重号，
 * 也不会有「每天一个 key 无限堆积」的问题。
 *
 * <p>抽成组件是为了让「正常提交预约」和「候补确认转正」共用同一套编号规则。
 */
@Component
public class ReserveNoUtils {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofHours(48);

    @Autowired
    private RedisUtils redisUtils;

    public String next(LocalDate date) {
        String key = RedisKeyConst.reservationSeqKey(date);
        long seq = redisUtils.incrementWithTtl(key, TTL);
        return "R-" + YYYYMMDD.format(date) + "-" + String.format("%04d", seq);
    }
}
