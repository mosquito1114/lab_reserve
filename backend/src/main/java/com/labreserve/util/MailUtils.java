package com.labreserve.util;

import com.labreserve.common.exception.BizException;
import com.labreserve.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 邮件发送工具组件。
 *
 * <p>只负责「把一封纯文本邮件交给 SMTP」，不含业务含义：
 * 渲染标题/正文在 {@link com.labreserve.mq.EmailStreamConsumer}，
 * 重试与死信也由消费者负责。
 *
 * <p>SMTP 账号密码来自 {@code .env} 的 {@code MAIL_USERNAME} / {@code MAIL_PASSWORD}
 * （QQ 邮箱必须用<b>授权码</b>，不是登录密码）。
 * 未配置时 {@link #isConfigured()} 返回 false，调用方应给出明确提示而不是抛 NPE。
 */
@Slf4j
@Component
public class MailUtils {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MailProperties mailProperties;

    /** 发件邮箱（同时是 SMTP 登录账号） */
    @Value("${spring.mail.username:}")
    private String fromAddress;

    /**
     * SMTP 是否已配置齐全（账号存在且不是占位符）。
     */
    public boolean isConfigured() {
        return StringUtils.hasText(fromAddress) && !fromAddress.startsWith("change-me");
    }

    /**
     * 发送一封纯文本邮件。
     *
     * @throws BizException 未配置 SMTP 时立刻失败（调用方据此给出可读提示）
     * @throws org.springframework.mail.MailException 发送失败（网络 / 鉴权 / 收件人非法等）
     */
    public void send(String to, String subject, String text) {
        if (!isConfigured()) {
            throw BizException.badRequest("邮件服务未配置：请在 .env 里填写 MAIL_USERNAME 与 MAIL_PASSWORD（QQ 邮箱为 SMTP 授权码）");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("[邮件] 已发送 to={}, subject={}", to, subject);
    }

    /** 发件人显示名（用于拼装邮件标题前缀） */
    public String fromName() {
        return StringUtils.hasText(mailProperties.getFromName()) ? mailProperties.getFromName() : "实验室预约管理系统";
    }
}
