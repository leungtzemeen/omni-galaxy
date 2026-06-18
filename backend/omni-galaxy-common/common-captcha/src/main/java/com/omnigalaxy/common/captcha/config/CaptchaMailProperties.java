package com.omnigalaxy.common.captcha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件验证码发送配置。
 * 对应 application.yml 的 {@code captcha.mail} 前缀。
 * SMTP 连接参数（host/port/username/password）沿用 Spring Boot 标准的
 * {@code spring.mail.*}，此处仅维护业务层面的发件人与邮件主题。
 */
@Data
@ConfigurationProperties(prefix = "captcha.mail")
public class CaptchaMailProperties {

    /** 发件人地址，显示在收件人邮件客户端中 */
    private String from;

    /** 邮件主题，默认值适用于大多数场景 */
    private String subject = "OmniGalaxy 验证码";
}
