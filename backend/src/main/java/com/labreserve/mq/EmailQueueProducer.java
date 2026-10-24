package com.labreserve.mq;

import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件队列生产者（Redis Stream {@code lab:reserve:stream:msg:email}）。
 *
 * <p>只做一件事：把「哪条消息需要发邮件」丢进 Stream。
 * 真正发信、重试、死信由 {@link EmailStreamConsumer} 负责，
 * 这样业务代码调用 {@code MessageService.send} 后立刻返回，不会被 SMTP 阻塞。
 */
@Slf4j
@Component
public class EmailQueueProducer {

    @Autowired
    private RedisUtils redisUtils;

    /**
     * 确保消费组存在（{@code XGROUP CREATE ... MKSTREAM}）。
     * 已存在时 Redis 会报 {@code BUSYGROUP}，这里吞掉。
     */
    public void ensureGroup() {
        StreamOperations<String, Object, Object> stream = redisUtils.template().opsForStream();
        try {
            stream.createGroup(RedisKeyConst.STREAM_EMAIL, ReadOffset.from("0"), RedisKeyConst.STREAM_EMAIL_GROUP);
            log.info("[邮件队列] 消费组 {} 已创建", RedisKeyConst.STREAM_EMAIL_GROUP);
        } catch (Exception e) {
            // BUSYGROUP：消费组已存在，属正常情况。
            // Lettuce 会把真正的错误包在 cause 里（外层只有 "Error in execution"），所以要沿 cause 链找一次。
            if (!isBusyGroup(e)) {
                log.debug("[邮件队列] 消费组检查异常：{}", e.getMessage());
            }
        }
    }

    /** 判断异常链里是否包含 BUSYGROUP（消费组已存在） */
    private boolean isBusyGroup(Throwable e) {
        Throwable cursor = e;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        return false;
    }

    /**
     * 入队一条邮件任务。
     *
     * @param messageId {@code tb_message.id}
     */
    public void enqueue(Long messageId) {
        ensureGroup();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("msgId", String.valueOf(messageId));
        body.put("enqueuedAt", String.valueOf(System.currentTimeMillis()));
        StreamOperations<String, Object, Object> stream = redisUtils.template().opsForStream();
        RecordId recordId = stream.add(StreamRecords.newRecord()
                .in(RedisKeyConst.STREAM_EMAIL)
                .ofMap(body));
        // 近似裁剪（~），防止 Stream 无限增长；裁剪不会影响 PEL 中的未确认消息
        stream.trim(RedisKeyConst.STREAM_EMAIL, RedisKeyConst.STREAM_EMAIL_MAX_LEN, true);
        log.info("[邮件队列] 入队 msgId={}, recordId={}", messageId, recordId);
    }

    /**
     * 转入死信队列。
     *
     * @param messageId {@code tb_message.id}
     * @param reason    最后一次失败原因
     */
    public void deadLetter(Long messageId, String reason) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("msgId", String.valueOf(messageId));
        body.put("reason", reason == null ? "" : reason);
        body.put("deadAt", String.valueOf(System.currentTimeMillis()));
        redisUtils.template().opsForStream().add(StreamRecords.newRecord()
                .in(RedisKeyConst.STREAM_EMAIL_DEAD)
                .ofMap(body));
        log.warn("[邮件队列] 转入死信 msgId={}, reason={}", messageId, reason);
    }
}
