package com.labreserve.common.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.labreserve.common.enums.SensitiveType;
import com.labreserve.util.SensitiveSerializer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏注解。<b>默认安全</b>——标了它的字段永远输出脱敏值。
 *
 * <pre>{@code
 * public class UserVO {
 *     @Sensitive(SensitiveType.PHONE)
 *     private String phone;      // 输出 138****1234
 * }
 * }</pre>
 *
 * <p>关于「管理员可见完整手机号」的例外（{@code revealPhone=true}）：
 * 该场景<b>不复用</b>这个注解，而是由用户模块返回另一个不带注解的 VO，
 * 并在接口层强制写 {@code QUERY_PHONE} 操作日志。
 * 这样可以避免引入 ThreadLocal 开关（在异步线程里会失效）。
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {

    SensitiveType value() default SensitiveType.DEFAULT;
}
