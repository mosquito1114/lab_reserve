package com.labreserve.rag.pipeline;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 摄取链路的 traceId 来源（{@code tb_rag_pipeline_step.trace_id}）。
 *
 * <p>每条摄取调用产生一个 traceId，贯穿 EXTRACT → SANITIZE → CHUNK → EMBED → UPSERT；
 * 调用 {@link com.labreserve.rag.controller.RagAdminController} 的 HTTP 请求本身没有
 * traceId 字段（接口约定不暴露 traceId 给前端），由本类生成。
 *
 * <p>traceId 与 {@code tb_rag_message.trace_id}（R3 消息表）统一由本类生成，
 * 排查时一条 admin 操作对应多条 step_row + 最后落库时一条 message，串起来。
 *
 * <p>格式：{@code "rag-" + 32 字符 UUID（去掉 -）}，长度 36，全 ASCII。
 */
@Component
public class TraceContext {

    /**
     * 生成一个新 traceId。
     *
     * @return 形如 {@code "rag-7d3a2b1c..."} 的字符串
     */
    public String newId() {
        return "rag-" + UUID.randomUUID().toString().replace("-", "");
    }
}
