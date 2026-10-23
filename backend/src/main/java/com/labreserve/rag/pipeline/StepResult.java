package com.labreserve.rag.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 执行结果（{@link AbstractPipelineStep#apply(StepContext)} 的返回）。
 *
 * <p>两态：
 * <ul>
 *   <li>成功 —— {@link #isSuccess()} true，{@link #meta()} 携带统计数据
 *       （会由基类序列化到 {@code tb_rag_pipeline_step.output_meta}）</li>
 *   <li>失败 —— {@link #isSuccess()} false，{@link #errorMsg()} 携带错误信息
 *       （会由基类写到 {@code error_msg} 列）</li>
 * </ul>
 *
 * <p>不区分"业务预期失败"与"系统异常"——全部走 {@link #fail(String)}；由上层
 * {@link BizException} 或全局异常处理器统一处理 HTTP 翻译。
 *
 * <h3>可变 vs 不可变</h3>
 * 业务步骤通常按"成功 = 元数据 map + 一些统计"的形态构造。本类采用 <b>可变</b> map 风格
 * 工厂：{@link #ok()} / {@link #ok(Map)} / {@link #put(String, Object)} 链式拼接。
 * 由于只在单个线程的单个 Step 中使用，没有跨线程共享，不会被踩。
 */
public final class StepResult {

    private final boolean success;
    private final Map<String, Object> meta;
    private final String errorMsg;

    private StepResult(boolean success, Map<String, Object> meta, String errorMsg) {
        this.success = success;
        this.meta = meta;
        this.errorMsg = errorMsg;
    }

    // --------------------------------------------------------------
    // 工厂
    // --------------------------------------------------------------

    public static StepResult ok() {
        return new StepResult(true, new LinkedHashMap<>(), null);
    }

    public static StepResult ok(Map<String, Object> meta) {
        return new StepResult(true, meta == null ? new LinkedHashMap<>() : meta, null);
    }

    public static StepResult fail(String errorMsg) {
        return new StepResult(false, new LinkedHashMap<>(), errorMsg);
    }

    // --------------------------------------------------------------
    // 链式
    // --------------------------------------------------------------

    /**
     * 追加 / 覆盖一个统计字段。注意必须<b>在 {@link #ok()} 之后</b>调用。
     */
    public StepResult put(String key, Object value) {
        meta.put(key, value);
        return this;
    }

    // --------------------------------------------------------------
    // 访问器
    // --------------------------------------------------------------

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> meta() {
        return meta;
    }

    public String errorMsg() {
        return errorMsg;
    }
}
