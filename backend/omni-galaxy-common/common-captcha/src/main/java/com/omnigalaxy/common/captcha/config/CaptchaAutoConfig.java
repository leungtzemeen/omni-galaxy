package com.omnigalaxy.common.captcha.config;

import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.captcha.manager.RedisOtpManager;
import com.omnigalaxy.common.captcha.sender.CaptchaSender;
import com.omnigalaxy.common.captcha.sender.impl.ConsoleCaptchaSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class CaptchaAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public OtpManager otpManager(StringRedisTemplate redisTemplate) {
        return new RedisOtpManager(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public CaptchaSender captchaSender() {
        return new ConsoleCaptchaSender();
    }
}
