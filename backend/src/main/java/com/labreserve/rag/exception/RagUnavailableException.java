package com.labreserve.rag.exception;

import com.labreserve.common.result.ResultCode;

/**
 * RAG 服务不可用异常。
 *
 * <p>由 {@code SafeStep} 在某一步骤超时 / 失败 / 降级路径走尽时抛出，
 * 由 {@link com.labreserve.common.exception.GlobalExceptionHandler} 翻译为
 * HTTP 503 + {@link ResultCode#RAG_UNAVAILABLE} 响应体。
 *
 * <p><b>何时抛</b>：
 * <ul>
 *   <li>LLM 调用超时且降级路径也已失败</li>
 *   <li>Qdrant 完全不可达且业务 Tool 也不可用</li>
 *   <li>整个 pipeline 5 步全失败</li>
 * </ul>
 *
 * <p>不希望与 {@link com.labreserve.common.exception.BizException} 混淆——
 * 这类是"业务预先定义的可预期失败",走 4xx;RAG 是"外部依赖不可用",走 5xx。
 *
 * @see com.labreserve.rag.service.SafeStep
 */
public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message) {
        super(message);
    }

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RagUnavailableException(ResultCode resultCode, String message) {
        super(message);
    }
}
