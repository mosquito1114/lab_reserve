package com.labreserve.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 开放星期工具组件。
 *
 * <p>数据库里 {@code tb_laboratory.open_weekdays} 是逗号分隔的 1–7（1 = 周一），
 * 这里负责解析、校验与转成中文展示。
 */
@Component
public class WeekdayUtils {

    private static final String[] NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /** 解析成 1–7 的集合；非法字符忽略 */
    public Set<Integer> parse(String openWeekdays) {
        Set<Integer> days = new TreeSet<>();
        if (openWeekdays == null || openWeekdays.isBlank()) {
            return days;
        }
        for (String part : openWeekdays.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                int day = Integer.parseInt(token);
                if (day >= 1 && day <= 7) {
                    days.add(day);
                }
            } catch (NumberFormatException ignored) {
                // 非法值忽略，由 isValid 统一校验
            }
        }
        return days;
    }

    /** 校验：非空、逗号分隔、每个值 1–7 且不重复 */
    public boolean isValid(String openWeekdays) {
        if (openWeekdays == null || openWeekdays.isBlank()) {
            return false;
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (String part : openWeekdays.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                return false;
            }
            try {
                int day = Integer.parseInt(token);
                if (day < 1 || day > 7 || !seen.add(day)) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return !seen.isEmpty();
    }

    /** 规范化：去空格、升序、去重，如 "5, 1,3" → "1,3,5" */
    public String normalize(String openWeekdays) {
        return String.join(",", new TreeSet<>(parse(openWeekdays)).stream().map(String::valueOf).toList());
    }

    /** 转中文展示：1,2,3,4,5 → 周一至周五；1,3,5 → 周一、周三、周五 */
    public String toText(String openWeekdays) {
        Set<Integer> days = parse(openWeekdays);
        if (days.isEmpty()) {
            return "";
        }
        List<Integer> list = new ArrayList<>(days);
        boolean consecutive = true;
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) != list.get(i - 1) + 1) {
                consecutive = false;
                break;
            }
        }
        if (consecutive && list.size() > 1) {
            return NAMES[list.get(0) - 1] + "至" + NAMES[list.get(list.size() - 1) - 1];
        }
        return String.join("、", list.stream().map(day -> NAMES[day - 1]).toList());
    }

    /** 某天是否开放 */
    public boolean contains(String openWeekdays, int weekday) {
        return parse(openWeekdays).contains(weekday);
    }

    /** 中文名，1 → 周一 */
    public String nameOf(int weekday) {
        return (weekday >= 1 && weekday <= 7) ? NAMES[weekday - 1] : "";
    }
}
