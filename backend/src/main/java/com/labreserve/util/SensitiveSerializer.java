package com.labreserve.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.labreserve.common.annotation.Sensitive;
import com.labreserve.common.enums.SensitiveType;

import java.io.IOException;

/**
 * 脱敏序列化器。
 *
 * <p>实现 {@link ContextualSerializer} 是为了在序列化时读取字段上的
 * {@link Sensitive} 注解，拿到具体的 {@link SensitiveType}。
 * 由 {@code Sensitive} 注解通过 {@code @JsonSerialize(using = ...)} 关联。
 */
public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private final SensitiveType type;

    /** Jackson 需要一个无参构造 */
    public SensitiveSerializer() {
        this.type = SensitiveType.DEFAULT;
    }

    public SensitiveSerializer(SensitiveType type) {
        this.type = type;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        // 本类由 Jackson 实例化，拿不到 Spring 注入，因此用静态兜底方法
        gen.writeString(SensitiveUtils.staticMask(value, type));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property == null) {
            return this;
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(Sensitive.class);
        }
        if (annotation != null) {
            return new SensitiveSerializer(annotation.value());
        }
        return this;
    }
}
