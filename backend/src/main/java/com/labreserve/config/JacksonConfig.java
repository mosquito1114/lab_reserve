package com.labreserve.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.labreserve.common.constant.CommonConst;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 全局配置。
 *
 * <p>统一日期时间格式，避免前端拿到 ISO-8601 带 T 的字符串：
 * <ul>
 *   <li>{@code LocalDateTime} → {@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>{@code LocalDate} → {@code yyyy-MM-dd}</li>
 *   <li>{@code LocalTime} → {@code HH:mm}</li>
 * </ul>
 *
 * <p>关于 {@code Long} 是否需要转 String 防前端精度丢失：
 * 本项目主键为 {@code BIGINT AUTO_INCREMENT}，实际值远小于
 * JavaScript 的安全整数上限（2^53-1），因此<b>不做转换</b>，
 * 前端可以直接按 number 处理，少一层心智负担。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(CommonConst.DATETIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(CommonConst.DATE_PATTERN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(CommonConst.TIME_PATTERN);

        return builder -> builder
                .timeZone(TimeZone.getTimeZone(CommonConst.ZONE_ID))
                .serializers(
                        new LocalDateTimeSerializer(dateTimeFormatter),
                        new LocalDateSerializer(dateFormatter),
                        new LocalTimeSerializer(timeFormatter))
                .deserializers(
                        new LocalDateTimeDeserializer(dateTimeFormatter),
                        new LocalDateDeserializer(dateFormatter),
                        new LocalTimeDeserializer(timeFormatter))
                // 枚举按 name 序列化/反序列化
                .featuresToEnable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.FAIL_ON_EMPTY_BEANS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }
}
