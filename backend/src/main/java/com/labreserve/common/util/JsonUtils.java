package com.labreserve.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具。
 *
 * <p>主要用途：资源更新日志与预约调整申请的「变更前 / 变更后」快照序列化
 * （对应 {@code tb_resource_update_log.before_json/after_json}、
 * {@code tb_reservation_change_request.before_json/after_json}）。
 *
 * <p><b>序列化失败返回 null 而不是抛异常</b>——快照只是留痕，不该阻断主业务。
 */
@Slf4j
public final class JsonUtils {

    private JsonUtils() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // 日期用字符串而不是时间戳
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 未知字段不报错，方便后续给快照加字段
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // 不输出 null 字段，让快照更紧凑
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 对象转 JSON 字符串；失败返回 null */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[JsonUtils] 序列化失败，类={}", obj.getClass().getName(), e);
            return null;
        }
    }

    /** JSON 字符串转对象；失败返回 null */
    public static <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("[JsonUtils] 反序列化失败，目标类={}", clazz.getName(), e);
            return null;
        }
    }

    /** JSON 字符串转泛型对象；失败返回 null */
    public static <T> T parse(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            log.warn("[JsonUtils] 反序列化失败", e);
            return null;
        }
    }

    /** 暴露给需要精细控制的场景 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
