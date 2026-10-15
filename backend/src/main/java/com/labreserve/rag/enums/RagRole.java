package com.labreserve.rag.enums;

import com.labreserve.common.exception.BizException;
import lombok.Getter;

/**
 * RAG 会话消息角色（{@code tb_rag_message.role}）。
 *
 * <p>R3 启用会话与消息表；R1 先把枚举定下，避免后续大批改 mapper 主键语义。
 *
 * <ul>
 *   <li>{@link #USER}：用户提问（一问一答里的"问"）</li>
 *   <li>{@link #ASSISTANT}：模型答复（一问一答里的"答"，含 tool_calls / citations 等补充字段）</li>
 *   <li>{@link #TOOL}：Tool 调用结果（"答"补充字段）</li>
 *   <li>{@link #SYSTEM}：摘要压缩、规则提示（一问一答里的非显式角色）</li>
 * </ul>
 */
@Getter
public enum RagRole {

    USER("USER"),
    ASSISTANT("ASSISTANT"),
    TOOL("TOOL"),
    SYSTEM("SYSTEM"),
    ;

    private final String code;

    RagRole(String code) {
        this.code = code;
    }

    public static RagRole requireOf(String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("消息角色不能为空");
        }
        for (RagRole r : values()) {
            if (r.code.equalsIgnoreCase(code.trim())) {
                return r;
            }
        }
        throw BizException.badRequest("未知消息角色: " + code);
    }
}
