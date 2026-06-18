package com.omnigalaxy.common.captcha.sender.impl;

import com.omnigalaxy.common.captcha.config.CaptchaMailProperties;
import com.omnigalaxy.common.captcha.sender.CaptchaSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Spring Mail 邮件验证码发送器（EMAIL 渠道生产实现）。
 *
 * <p>SMTP 连接参数沿用 Spring Boot 标准的 {@code spring.mail.*} 自动配置，
 * 业务参数（发件人、主题）通过 {@link CaptchaMailProperties} 注入。
 * 实例由 {@link com.omnigalaxy.common.captcha.config.CaptchaAutoConfig} 条件装配，
 * 需 {@code JavaMailSender} Bean 存在（即配置了 {@code spring.mail.host}）方可激活。
 */
@Slf4j
@RequiredArgsConstructor
public class SpringMailEmailCaptchaSender implements CaptchaSender {

    private final JavaMailSender       mailSender;
    private final CaptchaMailProperties properties;

    @Override
    public void send(String identityType, String identifier, String code) {
        log.info(">>>> [MailSender] 发送邮件验证码 identifier: {}", mask(identifier));
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getFrom());
            message.setTo(identifier);
            message.setSubject(properties.getSubject());
            message.setText("您的验证码为：" + code + "，5 分钟内有效，请勿泄露给他人。");
            mailSender.send(message);
            log.info("<<<< [MailSender] 邮件发送成功 identifier: {}", mask(identifier));
        } catch (Exception e) {
            log.error(">>>> [MailSender] 邮件发送异常 identifier: {}", mask(identifier), e);
            throw new RuntimeException("邮件发送失败，请稍后重试", e);
        }
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "****";
        int at = s.indexOf('@');
        if (at > 0) {
            return s.substring(0, Math.min(3, at)) + "****" + s.substring(at);
        }
        return s.substring(0, 3) + "****";
    }
}
