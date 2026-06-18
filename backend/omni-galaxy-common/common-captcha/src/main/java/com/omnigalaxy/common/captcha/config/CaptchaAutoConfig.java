package com.omnigalaxy.common.captcha.config;

import com.omnigalaxy.common.captcha.manager.EasyCaptchaVerificationManager;
import com.omnigalaxy.common.captcha.manager.HumanVerificationManager;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.captcha.manager.RedisOtpManager;
import com.omnigalaxy.common.captcha.sender.CaptchaSender;
import com.omnigalaxy.common.captcha.sender.impl.AliyunSmsCaptchaSender;
import com.omnigalaxy.common.captcha.sender.impl.ConsoleCaptchaSender;
import com.omnigalaxy.common.captcha.sender.impl.DispatchingCaptchaSender;
import com.omnigalaxy.common.captcha.sender.impl.SpringMailEmailCaptchaSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * common-captcha 自动装配入口。
 *
 * <p>渠道发送器装配策略：
 * <ul>
 *   <li>未配置任何渠道 → {@link ConsoleCaptchaSender}（控制台打印，开发/测试默认）</li>
 *   <li>仅配置阿里云 SMS → {@link DispatchingCaptchaSender}（仅 PHONE 可用）</li>
 *   <li>仅配置 Spring Mail → {@link DispatchingCaptchaSender}（仅 EMAIL 可用）</li>
 *   <li>两者均配置 → {@link DispatchingCaptchaSender}（PHONE + EMAIL 全渠道）</li>
 * </ul>
 *
 * <p>业务方可通过自定义 {@code @Bean CaptchaSender} 完全覆盖自动装配逻辑。
 */
@AutoConfiguration
@EnableConfigurationProperties({AliyunSmsProperties.class, CaptchaMailProperties.class})
public class CaptchaAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public OtpManager otpManager(StringRedisTemplate redisTemplate) {
        return new RedisOtpManager(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public HumanVerificationManager humanVerificationManager(StringRedisTemplate redisTemplate) {
        return new EasyCaptchaVerificationManager(redisTemplate);
    }

    // ── 渠道级 Sender（各自按条件激活）─────────────────────────────────────────────

    /** 阿里云 SMS：配置了 access-key-id 时激活 */
    @Bean
    @ConditionalOnProperty(prefix = "captcha.sms.aliyun", name = "access-key-id")
    public AliyunSmsCaptchaSender aliyunSmsCaptchaSender(AliyunSmsProperties properties) {
        return new AliyunSmsCaptchaSender(properties);
    }

    /** Spring Mail 邮件：容器中存在 JavaMailSender Bean 时激活（由 spring-boot-starter-mail 注册） */
    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    public SpringMailEmailCaptchaSender springMailEmailCaptchaSender(
            JavaMailSender mailSender, CaptchaMailProperties properties) {
        return new SpringMailEmailCaptchaSender(mailSender, properties);
    }

    // ── 顶层 CaptchaSender：有渠道 Sender 则用分发器，否则降级控制台 ─────────────────

    /**
     * 统一入口：通过 {@link ObjectProvider} 懒探测已注册的渠道 Sender，
     * 有任意渠道配置则返回 {@link DispatchingCaptchaSender}，全无则降级为控制台打印（开发模式）。
     * 未配置的渠道在运行时调用 {@code send()} 时快速失败，暴露配置缺失而非静默丢失。
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaSender.class)
    public CaptchaSender captchaSender(
            ObjectProvider<AliyunSmsCaptchaSender> smsProvider,
            ObjectProvider<SpringMailEmailCaptchaSender> emailProvider) {
        Map<String, CaptchaSender> senders = new LinkedHashMap<>();
        smsProvider.ifAvailable(s -> senders.put("PHONE", s));
        emailProvider.ifAvailable(s -> senders.put("EMAIL", s));
        if (senders.isEmpty()) {
            return new ConsoleCaptchaSender();
        }
        return new DispatchingCaptchaSender(senders);
    }
}
