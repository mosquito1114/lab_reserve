package com.labreserve.rag.adapter;

import com.labreserve.rag.model.vo.RagStreamItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 适配器：把 Reactor 的事件流桥接成 Spring MVC 的 {@link SseEmitter}。
 *
 * <p><b>为什么不用 WebFlux</b>：项目后端是纯 Spring MVC（{@code spring-boot-starter-web}），
 * 见 {@code docs/rag-design.md} §4.7 / §十一 的 SSE 选型决策。RAG 单独引 webflux 会让全站变成
 * "两套 web 栈"，且要重做鉴权过滤器链与 {@code @OpLog} 切面的响应式适配，得不偿失。
 *
 * <h3>桥接原理</h3>
 * <ol>
 *   <li>Controller 拿到 {@link SseEmitter}（长连接，浏览器侧用 {@code fetch} + ReadableStream
 *       或 {@code EventSource} 消费；本项目用 POST + JSON，故前端用 fetch 读流）</li>
 *   <li>订阅上游 {@code Flux<RagStreamItem>}，每个事件立刻 {@code emitter.send(...)}</li>
 *   <li>上游结束 → 补发一个 {@code done} 事件（若业务侧未发）→ {@code emitter.complete()}</li>
 *   <li>上游异常 → 发 {@code error} 事件 → {@code emitter.complete()}</li>
 * </ol>
 *
 * <p><b>线程模型</b>：{@code SseEmitter.send} 是同步阻塞的（写 Servlet 输出流），
 * 而 LLM 产出速度可能远快于客户端消费。这里用 {@code publishOn(boundedElastic)} 把网络 IO
 * 从 Reactor 的 event-loop 挪走，避免阻塞其他请求。
 */
@Slf4j
@Component
public class RagSseEmitterAdapter {

    /** 单条 SSE 连接超时（5 分钟；防止浏览器挂着不关导致线程泄漏） */
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * 把事件流桥接成 SSE。
     *
     * @param eventFlux 已打好 {@link RagStreamItem.Kind} 标签的事件流
     * @return 立即返回的 SseEmitter（内容异步推送）
     */
    public SseEmitter bridge(Flux<RagStreamItem> eventFlux) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        AtomicBoolean done = new AtomicBoolean(false);

        eventFlux
                .publishOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe(
                        item -> {
                            if (!send(emitter, item.getKind().name().toLowerCase(), item)) {
                                // 客户端已断开，取消上游订阅（由 publishOn 的背压自然收敛）
                                done.set(true);
                            }
                        },
                        error -> {
                            log.warn("[RAG][SSE] 事件流异常: {}", rootMessage(error));
                            if (done.compareAndSet(false, true)) {
                                send(emitter, "error", new RagStreamItem(
                                        RagStreamItem.Kind.ERROR, null, null, null,
                                        null, null, null, rootMessage(error)));
                                emitter.complete();
                            }
                        },
                        () -> {
                            if (done.compareAndSet(false, true)) {
                                log.debug("[RAG][SSE] 事件流正常结束");
                                // 业务侧通常已发过 DONE；这里不重复发，交由上游决定
                                emitter.complete();
                            }
                        });

        // 客户端主动断开会触发该回调，打日志并释放引用
        emitter.onCompletion(() -> log.debug("[RAG][SSE] 连接完成"));
        emitter.onTimeout(() -> {
            log.warn("[RAG][SSE] 连接超时 {}ms，主动断开", TIMEOUT_MS);
            emitter.complete();
        });
        emitter.onError(e -> log.warn("[RAG][SSE] 连接异常: {}", rootMessage(e)));

        return emitter;
    }

    /**
     * 发送一个 SSE 事件。客户端已断开时返回 {@code false}（调用方据此判定上游可取消）。
     */
    private boolean send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            log.debug("[RAG][SSE] 发送失败(event={}): {}", eventName, e.getMessage());
            return false;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}