package com.labreserve.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.mapper.MessageMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.entity.Message;
import com.labreserve.model.entity.User;
import com.labreserve.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息数据维护任务（低频，跟邮件投递无关）。
 *
 * <ol>
 *   <li><b>物理清理</b>：删除 30 天前「用户已删」的消息（{@code is_deleted=1}）。
 *       页面上的删除只是用户视角的软删，正文要留一段时间给管理员排查。</li>
 *   <li><b>未读数校验</b>：按库重算每个用户的未读数写回 Redis，修正因异常退出造成的计数漂移。</li>
 * </ol>
 *
 * <p>两个任务都加 {@code lab:reserve:lock:job:*} 的分布式锁语义由单实例部署保证；
 * 多实例部署时如需互斥，把任务体包进 {@code RedisLockTemplate} 即可。
 */
@Slf4j
@Component
public class MessageMaintenanceJob {

    /** 已删消息保留天数 */
    private static final int KEEP_DELETED_DAYS = 30;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtils redisUtils;

    /** 每天 03:30 物理清理已删消息 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void purgeDeletedMessages() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(KEEP_DELETED_DAYS);
        int deleted = messageMapper.delete(Wrappers.<Message>lambdaQuery()
                .eq(Message::getIsDeleted, 1)
                .lt(Message::getUpdatedAt, deadline));
        if (deleted > 0) {
            log.info("[消息] 物理清理 {} 条 {} 天前的已删消息", deleted, KEEP_DELETED_DAYS);
        }
    }

    /** 每小时校正一次未读计数（顺带清掉已注销用户残留的 key） */
    @Scheduled(cron = "0 5 * * * ?")
    public void reconcileUnreadCounters() {
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .select(User::getId)
                .eq(User::getStatus, 1));
        int fixed = 0;
        for (User user : users) {
            long dbCount = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                    .eq(Message::getReceiverId, user.getId())
                    .eq(Message::getIsRead, 0)
                    .eq(Message::getIsDeleted, 0));
            String key = RedisKeyConst.inboxUnreadKey(user.getId());
            String cached = redisUtils.get(key);
            if (cached == null || !cached.equals(String.valueOf(dbCount))) {
                redisUtils.set(key, String.valueOf(dbCount));
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("[消息] 未读计数校正完成，修正 {} 个用户", fixed);
        }
    }
}
